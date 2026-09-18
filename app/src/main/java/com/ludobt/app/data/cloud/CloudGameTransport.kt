package com.ludobt.app.data.cloud

import android.util.Log
import com.ludobt.app.domain.engine.LudoGameEngine
import com.ludobt.app.domain.model.GameMessage
import com.ludobt.app.domain.model.GameMode
import com.ludobt.app.domain.model.GameState
import com.ludobt.app.domain.model.Player
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.domain.model.PlayerType
import com.ludobt.app.domain.transport.ConnectionStatus
import com.ludobt.app.domain.transport.DiscoveredDevice
import com.ludobt.app.domain.transport.GameTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class CloudEnvelope(
    val senderId: String,
    val payload: String
)

/**
 * 100% Zero-Config, High-Speed Cloud Transport for Online Multiplayer.
 * Runs pure MQTT 3.1.1 over direct TCP with dual-broker automatic failover:
 * Primary: broker.hivemq.com (1883)
 * Secondary / Fallback: broker.emqx.io (1883)
 *
 * Eliminates all TLS/SSL certificate trust anchor errors and ISP web filtering issues.
 */
class CloudGameTransport : GameTransport {

    companion object {
        private const val TAG = "CloudTransport"
        private val BROKERS = listOf(
            "broker.hivemq.com" to 1883,
            "broker.emqx.io" to 1883
        )
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val PING_INTERVAL_MS = 20_000L
    }

    private val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<String>>(emptyList())
    override val connectedPeers: StateFlow<List<String>> = _connectedPeers.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<GameMessage>(extraBufferCapacity = 64)
    override val incomingMessages: Flow<GameMessage> = _incomingMessages.asSharedFlow()

    // Online Lobby State Flows
    private val _currentRoomPin = MutableStateFlow<String?>(null)
    val currentRoomPin: StateFlow<String?> = _currentRoomPin.asStateFlow()

    private val _lobbyPlayers = MutableStateFlow<List<Player>>(emptyList())
    val lobbyPlayers: StateFlow<List<Player>> = _lobbyPlayers.asStateFlow()

    private val _lastError = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val lastError: Flow<String> = _lastError.asSharedFlow()

    private val localPlayerId = "PL_" + UUID.randomUUID().toString().substring(0, 8)
    private var localPlayerName = "Player"
    private var localPlayerColor = PlayerColor.RED
    private var isHost = false
    private var expectedPlayerCount = 2

    private var activeRoomPin: String? = null
    private var isQuickMatchSession = false

    private var activeSocket: Socket? = null
    private var socketOut: OutputStream? = null
    private var socketIn: InputStream? = null
    private var readerJob: Job? = null
    private var pingJob: Job? = null
    private var heartbeatJob: Job? = null
    private var joinWatcherJob: Job? = null
    private var quickMatchJob: Job? = null

    private val packetIdCounter = AtomicInteger(1)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    override fun getLocalDeviceName(): String = localPlayerName
    override fun getLocalPlayerId(): String = localPlayerId

    // ==========================================
    // Public Lobby Actions
    // ==========================================

    suspend fun createRoom(playerCount: Int, playerName: String, color: PlayerColor) {
        withContext(Dispatchers.IO) {
            try {
                disconnect()
                _connectionStatus.value = ConnectionStatus.CONNECTING
                localPlayerName = playerName
                localPlayerColor = color
                isHost = true
                expectedPlayerCount = if (playerCount == 4) 4 else 2

                val pin = (100000..999999).random().toString()
                activeRoomPin = pin
                _currentRoomPin.value = pin

                val cleanName = playerName.replace(" (You)", "").trim().ifBlank { "Host" }
                localPlayerName = cleanName
                val hostPlayer = Player(
                    id = localPlayerId,
                    name = cleanName,
                    color = color,
                    type = PlayerType.HUMAN,
                    isHost = true,
                    isReady = true
                )
                _lobbyPlayers.value = listOf(hostPlayer)
                _connectedPeers.value = listOf(hostPlayer.name)

                connectToBrokerWithFallback()
                subscribeTopic("ludobt/room/$pin")
                _connectionStatus.value = ConnectionStatus.CONNECTED_AS_HOST
                Log.d(TAG, "Room $pin successfully created by host $cleanName ($localPlayerId)")

                // Announce room presence periodically to sync connecting guests
                heartbeatJob?.cancel()
                heartbeatJob = transportScope.launch {
                    while (isActive && activeRoomPin == pin && _lobbyPlayers.value.size < expectedPlayerCount) {
                        broadcastMessage(GameMessage.RoomCreatedEvent(pin, localPlayerId))
                        delay(2500)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating room: ${e.message}", e)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _lastError.tryEmit("Failed to connect to cloud: ${e.localizedMessage ?: "Network error"}")
            }
        }
    }

    suspend fun joinRoom(roomPin: String, playerName: String, color: PlayerColor) {
        withContext(Dispatchers.IO) {
            try {
                val pin = roomPin.trim()
                if (pin.length != 6 || !pin.all { it.isDigit() }) {
                    _lastError.tryEmit("Please enter a valid 6-digit Room PIN")
                    return@withContext
                }

                disconnect()
                _connectionStatus.value = ConnectionStatus.CONNECTING
                val cleanName = playerName.replace(" (You)", "").trim().ifBlank { "Guest" }
                localPlayerName = cleanName
                localPlayerColor = color
                isHost = false
                activeRoomPin = pin
                _currentRoomPin.value = pin

                val guestPlayer = Player(
                    id = localPlayerId,
                    name = cleanName,
                    color = color,
                    type = PlayerType.HUMAN,
                    isHost = false,
                    isReady = true
                )
                _lobbyPlayers.value = listOf(guestPlayer)

                connectToBrokerWithFallback()
                subscribeTopic("ludobt/room/$pin")
                _connectionStatus.value = ConnectionStatus.CONNECTED_AS_CLIENT
                Log.d(TAG, "Guest $cleanName ($localPlayerId) connected to room $pin, sending LobbyJoin...")

                // Send LobbyJoin and retry every 1.5s until host acknowledges with LobbyUpdate
                joinWatcherJob?.cancel()
                joinWatcherJob = transportScope.launch {
                    var attempts = 0
                    while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED_AS_CLIENT && _lobbyPlayers.value.size < 2 && attempts < 10) {
                        broadcastMessage(
                            GameMessage.LobbyJoin(
                                playerId = localPlayerId,
                                playerName = cleanName,
                                requestedColor = color
                            )
                        )
                        delay(1500)
                        attempts++
                    }
                    if (isActive && isQuickMatchSession && _lobbyPlayers.value.size < 2) {
                        Log.d(TAG, "Quick match room inactive, switching to host own match...")
                        createRoom(expectedPlayerCount, playerName, color)
                        startQuickMatchAnnouncer(expectedPlayerCount, activeRoomPin ?: "", color)
                    } else if (attempts >= 10 && _lobbyPlayers.value.size < 2) {
                        _lastError.tryEmit("Host is not responding. Please check Room PIN and try again.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error joining room: ${e.message}", e)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _lastError.tryEmit("Failed to join room: ${e.localizedMessage ?: "Network error"}")
            }
        }
    }

    suspend fun quickMatch(playerCount: Int, playerName: String, color: PlayerColor) {
        withContext(Dispatchers.IO) {
            try {
                isQuickMatchSession = true
                _connectionStatus.value = ConnectionStatus.CONNECTING
                val count = if (playerCount == 4) 4 else 2
                expectedPlayerCount = count
                localPlayerName = playerName
                localPlayerColor = color

                connectToBrokerWithFallback()
                val qmTopic = "ludobt/qm/$count"
                subscribeTopic(qmTopic)

                // Listen for an active announcement for 2.5 seconds
                var foundPin: String? = null
                val listenJob = transportScope.launch {
                    incomingMessages.collect { msg ->
                        if (msg is GameMessage.RoomCreatedEvent && msg.hostPlayerId != localPlayerId && msg.roomPin.length == 6) {
                            foundPin = msg.roomPin
                        }
                    }
                }

                delay(2500)
                listenJob.cancel()

                if (foundPin != null) {
                    Log.d(TAG, "Quick Match found existing room: $foundPin")
                    joinRoom(foundPin!!, playerName, color)
                } else {
                    Log.d(TAG, "No active room found, creating new Quick Match room...")
                    createRoom(count, playerName, color)
                    val pin = activeRoomPin ?: return@withContext
                    startQuickMatchAnnouncer(count, pin, color)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in quickMatch", e)
                createRoom(playerCount, playerName, color)
            }
        }
    }

    private fun startQuickMatchAnnouncer(count: Int, pin: String, color: PlayerColor) {
        val qmTopic = "ludobt/qm/$count"
        quickMatchJob?.cancel()
        quickMatchJob = transportScope.launch {
            while (isActive && activeRoomPin == pin && _lobbyPlayers.value.size < count) {
                try {
                    val announcePayload = json.encodeToString(GameMessage.serializer(), GameMessage.RoomCreatedEvent(pin, localPlayerId))
                    val envelope = CloudEnvelope(senderId = localPlayerId, payload = announcePayload)
                    publishRaw(qmTopic, json.encodeToString(CloudEnvelope.serializer(), envelope))
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

    fun startOnlineGameAsHost() {
        if (!isHost) return
        val currentPlayers = _lobbyPlayers.value
        if (currentPlayers.size < 2) return

        transportScope.launch {
            launchGameWithPlayers(currentPlayers)
        }
    }

    private suspend fun launchGameWithPlayers(updated: List<Player>) {
        quickMatchJob?.cancel()
        heartbeatJob?.cancel()
        delay(400)

        val cleanConfigs = updated.map { it.name.replace(" (You)", "").trim().ifBlank { "Player" } to PlayerType.HUMAN }
        val baseInitialState = LudoGameEngine.createInitialGame(
            mode = GameMode.ONLINE,
            playerConfigs = cleanConfigs,
            colors = updated.map { it.color }
        )
        // Ensure player IDs match real lobby player IDs (localPlayerId) and clean names
        val syncedPlayers = baseInitialState.players.mapIndexed { idx, pl ->
            val p = updated.getOrNull(idx)
            pl.copy(
                id = p?.id ?: pl.id,
                name = p?.name?.replace(" (You)", "")?.trim() ?: pl.name
            )
        }
        val initialGameState = baseInitialState.copy(players = syncedPlayers)

        // Broadcast StartGame with assignedPlayerIndex for clients
        broadcastMessage(
            GameMessage.StartGame(
                initialGameState = initialGameState,
                assignedPlayerIndex = 1
            )
        )

        // Host enters game locally at seat 0
        _incomingMessages.tryEmit(
            GameMessage.StartGame(
                initialGameState = initialGameState,
                assignedPlayerIndex = 0
            )
        )
        Log.d(TAG, "Match started! Transferred players into GameState.")
    }

    fun isQuickMatch(): Boolean = isQuickMatchSession
    fun isHostRole(): Boolean = isHost

    // ==========================================
    // Incoming Message Handler
    // ==========================================

    private fun handleIncomingCloudMessage(msg: GameMessage) {
        when (msg) {
            is GameMessage.RoomCreatedEvent -> {
                _incomingMessages.tryEmit(msg)
            }

            is GameMessage.LobbyJoin -> {
                // Only host handles join requests
                if (isHost && msg.playerId != localPlayerId) {
                    val current = _lobbyPlayers.value
                    if (current.none { it.id == msg.playerId }) {
                        val hostColor = localPlayerColor
                        // In 2-player mode: assign diagonally opposite yard automatically
                        val assignedColor = if (expectedPlayerCount == 2) {
                            when (hostColor) {
                                PlayerColor.RED -> PlayerColor.YELLOW
                                PlayerColor.YELLOW -> PlayerColor.RED
                                PlayerColor.BLUE -> PlayerColor.GREEN
                                PlayerColor.GREEN -> PlayerColor.BLUE
                            }
                        } else {
                            val used = current.map { it.color }
                            if (msg.requestedColor != null && !used.contains(msg.requestedColor)) msg.requestedColor
                            else listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE).first { !used.contains(it) }
                        }

                        val cleanGuestName = msg.playerName.replace(" (You)", "").trim().ifBlank { "Guest" }
                        val newGuest = Player(
                            id = msg.playerId,
                            name = cleanGuestName,
                            color = assignedColor,
                            type = PlayerType.BLUETOOTH_REMOTE,
                            isHost = false,
                            isReady = true
                        )

                        val updated = current + newGuest
                        _lobbyPlayers.value = updated
                        _connectedPeers.value = updated.map { it.name }
                        Log.d(TAG, "Guest joined: ${newGuest.name}, assigned color: ${assignedColor.displayName}. Total players: ${updated.size}")

                        transportScope.launch {
                            broadcastMessage(
                                GameMessage.LobbyUpdate(
                                    players = updated,
                                    hostId = localPlayerId,
                                    roomPin = activeRoomPin ?: ""
                                )
                            )

                            // Automatically start match if lobby is full in quick match
                            if (isQuickMatchSession && updated.size >= expectedPlayerCount) {
                                launchGameWithPlayers(updated)
                            }
                        }
                    }
                }
            }

            is GameMessage.LobbyColorChange -> {
                if (isHost && expectedPlayerCount == 4) {
                    val current = _lobbyPlayers.value
                    val used = current.filter { it.id != msg.playerId }.map { it.color }
                    if (!used.contains(msg.newColor)) {
                        val updated = current.map { if (it.id == msg.playerId) it.copy(color = msg.newColor) else it }
                        _lobbyPlayers.value = updated
                        transportScope.launch {
                            broadcastMessage(
                                GameMessage.LobbyUpdate(
                                    players = updated,
                                    hostId = localPlayerId,
                                    roomPin = activeRoomPin ?: ""
                                )
                            )
                        }
                    }
                }
            }

            is GameMessage.LobbyUpdate -> {
                if (!isHost) {
                    val cleanPlayers = msg.players.map { p ->
                        val cleanName = p.name.replace(" (You)", "").trim()
                        if (p.id == localPlayerId) p.copy(type = PlayerType.HUMAN, name = cleanName)
                        else p.copy(type = PlayerType.BLUETOOTH_REMOTE, name = cleanName)
                    }
                    _lobbyPlayers.value = cleanPlayers
                    _connectedPeers.value = cleanPlayers.map { it.name }
                    Log.d(TAG, "LobbyUpdate received by guest. Total players now: ${cleanPlayers.size}")
                }
            }

            is GameMessage.StartGame -> {
                quickMatchJob?.cancel()
                joinWatcherJob?.cancel()
                heartbeatJob?.cancel()
                if (!isHost) {
                    val mySeat = msg.initialGameState.players.indexOfFirst { it.id == localPlayerId }
                    val finalMsg = if (mySeat >= 0) msg.copy(assignedPlayerIndex = mySeat) else msg
                    _incomingMessages.tryEmit(finalMsg)
                    Log.d(TAG, "StartGame received by guest for seat index: ${finalMsg.assignedPlayerIndex}")
                }
            }

            is GameMessage.DiceRolledEvent -> {
                _incomingMessages.tryEmit(msg)
            }

            is GameMessage.TokenMovedEvent -> {
                _incomingMessages.tryEmit(msg)
            }

            is GameMessage.TurnTimeoutEvent -> {
                _incomingMessages.tryEmit(msg)
            }

            is GameMessage.FullStateSync -> {
                _incomingMessages.tryEmit(msg)
            }

            is GameMessage.PlayerDisconnected -> {
                _incomingMessages.tryEmit(msg)
            }

            else -> {}
        }
    }

    private suspend fun broadcastMessage(message: GameMessage) {
        val pin = activeRoomPin ?: return
        withContext(Dispatchers.IO) {
            try {
                val payloadJson = json.encodeToString(GameMessage.serializer(), message)
                val envelope = CloudEnvelope(senderId = localPlayerId, payload = payloadJson)
                val envelopeJson = json.encodeToString(CloudEnvelope.serializer(), envelope)
                publishRaw("ludobt/room/$pin", envelopeJson)
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting message: ${e.message}")
            }
        }
    }

    override suspend fun sendMessage(message: GameMessage) {
        broadcastMessage(message)
    }

    override suspend fun startDiscovery() {}
    override suspend fun stopDiscovery() {}
    override suspend fun startHost(roomName: String, maxPlayers: Int) {
        createRoom(maxPlayers, "Host", PlayerColor.RED)
    }
    override suspend fun joinHost(deviceAddress: String) {
        joinRoom(deviceAddress, "Guest", PlayerColor.BLUE)
    }

    suspend fun changeOnlineColor(newColor: PlayerColor) {
        localPlayerColor = newColor
        if (isHost) {
            val current = _lobbyPlayers.value
            val used = current.filter { it.id != localPlayerId }.map { it.color }
            if (!used.contains(newColor)) {
                val updated = current.map { if (it.id == localPlayerId) it.copy(color = newColor) else it }
                _lobbyPlayers.value = updated
                broadcastMessage(GameMessage.LobbyUpdate(players = updated, hostId = localPlayerId, roomPin = activeRoomPin ?: ""))
            }
        } else {
            broadcastMessage(GameMessage.LobbyColorChange(playerId = localPlayerId, newColor = newColor))
        }
    }

    override suspend fun disconnect() {
        val pin = activeRoomPin
        if (pin != null) {
            try {
                broadcastMessage(GameMessage.PlayerDisconnected(localPlayerId))
            } catch (_: Exception) {}
        }
        isQuickMatchSession = false
        joinWatcherJob?.cancel()
        joinWatcherJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        quickMatchJob?.cancel()
        quickMatchJob = null
        pingJob?.cancel()
        pingJob = null
        readerJob?.cancel()
        readerJob = null

        withContext(Dispatchers.IO) {
            try {
                socketOut?.let { writeDisconnect(it) }
                socketOut?.flush()
            } catch (_: Exception) {}
            try { activeSocket?.close() } catch (_: Exception) {}
            activeSocket = null
            socketOut = null
            socketIn = null
        }

        activeRoomPin = null
        _currentRoomPin.value = null
        _lobbyPlayers.value = emptyList()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    // ==========================================
    // Pure Kotlin MQTT 3.1.1 Socket Implementation
    // ==========================================

    private suspend fun connectToBrokerWithFallback() {
        var connected = false
        var lastEx: Exception? = null

        for ((host, port) in BROKERS) {
            try {
                Log.d(TAG, "Attempting connection to broker $host:$port...")
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.soTimeout = 0
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)

                val out = socket.getOutputStream()
                val inp = socket.getInputStream()

                // Send MQTT CONNECT packet
                val clientId = "ludo_${localPlayerId}_${System.currentTimeMillis() % 10000}"
                writeConnect(out, clientId)
                out.flush()

                // Read CONNACK packet
                readConnack(inp)

                activeSocket = socket
                socketOut = out
                socketIn = inp
                connected = true
                Log.d(TAG, "Successfully connected to broker $host:$port with clientId $clientId")
                break
            } catch (e: Exception) {
                Log.w(TAG, "Failed connecting to broker $host:$port: ${e.message}")
                lastEx = e
            }
        }

        if (!connected) {
            throw lastEx ?: IOException("Unable to connect to any multiplayer cloud broker")
        }

        startReaderAndKeepAlive()
    }

    private fun startReaderAndKeepAlive() {
        readerJob?.cancel()
        readerJob = transportScope.launch {
            val inp = socketIn ?: return@launch
            try {
                while (isActive) {
                    val headerByte = inp.read()
                    if (headerByte == -1) break

                    val packetType = (headerByte and 0xF0) shr 4
                    val remainingLength = readVarLength(inp)
                    val packetData = ByteArray(remainingLength)
                    var bytesRead = 0
                    while (bytesRead < remainingLength) {
                        val count = inp.read(packetData, bytesRead, remainingLength - bytesRead)
                        if (count == -1) throw EOFException("Unexpected end of stream")
                        bytesRead += count
                    }

                    when (packetType) {
                        3 -> { // PUBLISH
                            handlePublishPacket(packetData)
                        }
                        13 -> { // PINGRESP
                            // Keepalive acknowledged
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Socket reader terminated: ${e.message}")
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }
            }
        }

        // Send PINGREQ every 20s
        pingJob?.cancel()
        pingJob = transportScope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                try {
                    val out = socketOut ?: break
                    synchronized(out) {
                        writePingReq(out)
                        out.flush()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Ping failed: ${e.message}")
                    break
                }
            }
        }
    }

    private fun handlePublishPacket(data: ByteArray) {
        try {
            if (data.size < 2) return
            val topicLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            if (data.size < 2 + topicLen) return

            val payloadStart = 2 + topicLen
            val payloadBytes = data.copyOfRange(payloadStart, data.size)
            val text = String(payloadBytes, StandardCharsets.UTF_8)

            // Envelope unwrapping: drop broadcasts echoed back to ourselves
            val (senderId, actualPayload) = try {
                val env = json.decodeFromString(CloudEnvelope.serializer(), text)
                env.senderId to env.payload
            } catch (_: Exception) {
                null to text
            }

            if (senderId != null && senderId == localPlayerId) {
                return // Drop our own echo!
            }

            val gameMsg = json.decodeFromString(GameMessage.serializer(), actualPayload)
            handleIncomingCloudMessage(gameMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming publish: ${e.message}", e)
        }
    }

    private suspend fun subscribeTopic(topic: String) {
        withContext(Dispatchers.IO) {
            val out = socketOut ?: return@withContext
            synchronized(out) {
                val pId = packetIdCounter.getAndIncrement() and 0xFFFF
                writeSubscribe(out, pId, topic)
                out.flush()
            }
            Log.d(TAG, "Subscribed to MQTT topic: $topic")
        }
    }

    private suspend fun publishRaw(topic: String, messageText: String) {
        withContext(Dispatchers.IO) {
            val out = socketOut ?: return@withContext
            synchronized(out) {
                writePublish(out, topic, messageText)
                out.flush()
            }
        }
    }

    // ==========================================
    // MQTT Packet Writers & Readers
    // ==========================================

    private fun writeConnect(out: OutputStream, clientId: String) {
        val clientBytes = clientId.toByteArray(StandardCharsets.UTF_8)
        val varHeader = byteArrayOf(
            0x00, 0x04, 'M'.code.toByte(), 'Q'.code.toByte(), 'T'.code.toByte(), 'T'.code.toByte(),
            0x04, // Protocol level 3.1.1
            0x02, // Clean session flag
            0x00, 0x3C // Keep alive: 60 seconds
        )

        val payload = ByteArrayOutputStream()
        payload.write(clientBytes.size shr 8)
        payload.write(clientBytes.size and 0xFF)
        payload.write(clientBytes)

        val remLen = varHeader.size + payload.size()
        out.write(0x10) // CONNECT packet type
        writeVarLength(out, remLen)
        out.write(varHeader)
        out.write(payload.toByteArray())
    }

    private fun readConnack(inp: InputStream) {
        val header = inp.read()
        if (header != 0x20) throw IOException("Expected CONNACK (0x20) but got: 0x${header.toString(16)}")
        val remLen = readVarLength(inp)
        val data = ByteArray(remLen)
        var read = 0
        while (read < remLen) {
            val count = inp.read(data, read, remLen - read)
            if (count == -1) throw EOFException("End of stream reading CONNACK")
            read += count
        }
        if (data.size >= 2 && data[1].toInt() != 0) {
            throw IOException("MQTT Connection rejected with code: ${data[1]}")
        }
    }

    private fun writeSubscribe(out: OutputStream, packetId: Int, topic: String) {
        val topicBytes = topic.toByteArray(StandardCharsets.UTF_8)
        val payload = ByteArrayOutputStream()
        payload.write(packetId shr 8)
        payload.write(packetId and 0xFF)
        payload.write(topicBytes.size shr 8)
        payload.write(topicBytes.size and 0xFF)
        payload.write(topicBytes)
        payload.write(0x00) // Requested QoS: 0

        val bytes = payload.toByteArray()
        out.write(0x82) // SUBSCRIBE packet type
        writeVarLength(out, bytes.size)
        out.write(bytes)
    }

    private fun writePublish(out: OutputStream, topic: String, message: String) {
        val topicBytes = topic.toByteArray(StandardCharsets.UTF_8)
        val msgBytes = message.toByteArray(StandardCharsets.UTF_8)

        val varHeaderAndPayload = ByteArrayOutputStream()
        varHeaderAndPayload.write(topicBytes.size shr 8)
        varHeaderAndPayload.write(topicBytes.size and 0xFF)
        varHeaderAndPayload.write(topicBytes)
        varHeaderAndPayload.write(msgBytes)

        val bytes = varHeaderAndPayload.toByteArray()
        out.write(0x30) // PUBLISH packet type (QoS 0)
        writeVarLength(out, bytes.size)
        out.write(bytes)
    }

    private fun writePingReq(out: OutputStream) {
        out.write(0xC0)
        out.write(0x00)
    }

    private fun writeDisconnect(out: OutputStream) {
        out.write(0xE0)
        out.write(0x00)
    }

    private fun writeVarLength(out: OutputStream, length: Int) {
        var x = length
        do {
            var encodedByte = x % 128
            x /= 128
            if (x > 0) encodedByte = encodedByte or 128
            out.write(encodedByte)
        } while (x > 0)
    }

    private fun readVarLength(input: InputStream): Int {
        var multiplier = 1
        var value = 0
        var digit: Int
        do {
            digit = input.read()
            if (digit == -1) throw EOFException("End of stream reading remaining length")
            value += (digit and 127) * multiplier
            multiplier *= 128
            if (multiplier > 128 * 128 * 128) throw IOException("Malformed remaining length in MQTT packet")
        } while ((digit and 128) != 0)
        return value
    }
}
