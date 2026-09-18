package com.ludobt.app.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.ludobt.app.domain.model.GameMessage
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.domain.transport.ConnectionStatus
import com.ludobt.app.domain.transport.DiscoveredDevice
import com.ludobt.app.domain.transport.GameTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BluetoothClassicTransport(
    private val context: Context
) : GameTransport {

    companion object {
        val LUDO_APP_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        private const val SERVICE_NAME = "LudoNearbyGame"
        private const val TAG = "LudoBT"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendMutex = Mutex()
    private val peerNames = ConcurrentHashMap<String, String>()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<String>>(emptyList())
    override val connectedPeers: StateFlow<List<String>> = _connectedPeers.asStateFlow()

    private var hostPlayerColor: PlayerColor = PlayerColor.RED
    private var lobbyPlayerCount: Int = 2
    private val peerRequestedColors = ConcurrentHashMap<String, PlayerColor>()
    private val _lobbyColors = MutableStateFlow<List<PlayerColor>>(listOf(PlayerColor.RED, PlayerColor.YELLOW))
    val lobbyColors: StateFlow<List<PlayerColor>> = _lobbyColors.asStateFlow()

    private fun recomputeLobbyColors(): List<PlayerColor> {
        val allColors = listOf(PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE)
        return if (lobbyPlayerCount == 2) {
            listOf(hostPlayerColor, hostPlayerColor.getOppositeColor())
        } else {
            val result = mutableListOf(hostPlayerColor)
            val available = allColors.filter { it != hostPlayerColor }.toMutableList()
            clientSockets.keys.forEach { address ->
                val requested = peerRequestedColors[address]
                if (requested != null && available.contains(requested)) {
                    result.add(requested)
                    available.remove(requested)
                } else if (available.isNotEmpty()) {
                    val assigned = available.removeAt(0)
                    peerRequestedColors[address] = assigned
                    result.add(assigned)
                }
            }
            while (result.size < lobbyPlayerCount && available.isNotEmpty()) {
                result.add(available.removeAt(0))
            }
            result
        }
    }

    fun setHostColor(color: PlayerColor, playerCount: Int) {
        hostPlayerColor = color
        lobbyPlayerCount = playerCount
        _lobbyColors.value = recomputeLobbyColors()
        if (_connectionStatus.value == ConnectionStatus.CONNECTED_AS_HOST) {
            transportScope.launch {
                val updateMsg = GameMessage.LobbySlotUpdate(
                    playerCount = lobbyPlayerCount,
                    hostName = getLocalDeviceName(),
                    connectedPeerNames = _connectedPeers.value,
                    colors = _lobbyColors.value
                )
                sendMessage(updateMsg)
            }
        }
    }

    suspend fun requestColorChange(newColor: PlayerColor) {
        if (_connectionStatus.value == ConnectionStatus.CONNECTED_AS_CLIENT) {
            sendMessage(GameMessage.LobbyColorChange(playerId = getLocalDeviceName(), newColor = newColor))
        } else if (_connectionStatus.value == ConnectionStatus.CONNECTED_AS_HOST) {
            setHostColor(newColor, lobbyPlayerCount)
        }
    }

    private val _incomingMessages = MutableSharedFlow<GameMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<GameMessage> = _incomingMessages.asSharedFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private val clientSockets = ConcurrentHashMap<String, BluetoothSocket>()
    private var hostSocket: BluetoothSocket? = null

    private var serverAcceptJob: Job? = null
    private var heartbeatJob: Job? = null
    private val readJobs = ConcurrentHashMap<String, Job>()

    private var discoveryReceiver: BroadcastReceiver? = null

    override fun getLocalDeviceName(): String {
        return try {
            bluetoothAdapter?.name?.ifBlank { "My Phone" } ?: "My Phone"
        } catch (_: Exception) {
            "My Phone"
        }
    }

    override suspend fun startDiscovery() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            return
        }

        _connectionStatus.value = ConnectionStatus.SCANNING
        _discoveredDevices.value = emptyList()

        // Include paired devices first with explicit label
        val paired = try {
            bluetoothAdapter.bondedDevices?.map { device ->
                val devName = try { device.name ?: "Bluetooth Device" } catch (_: Exception) { "Bluetooth Device" }
                DiscoveredDevice(name = "$devName (Paired)", address = device.address)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        _discoveredDevices.value = paired

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                        device?.let { dev ->
                            val name = try { dev.name ?: "Nearby Device" } catch (_: Exception) { "Nearby Device" }
                            val address = dev.address
                            val current = _discoveredDevices.value
                            if (current.none { it.address == address }) {
                                _discoveredDevices.value = current + DiscoveredDevice(name = name, address = address)
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (_connectionStatus.value == ConnectionStatus.SCANNING) {
                            _connectionStatus.value = ConnectionStatus.DISCONNECTED
                        }
                    }
                }
            }
        }

        try {
            context.registerReceiver(discoveryReceiver, filter)
            bluetoothAdapter.startDiscovery()
        } catch (_: Exception) {
            // Permission or platform issue
        }
    }

    override suspend fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            discoveryReceiver?.let {
                context.unregisterReceiver(it)
                discoveryReceiver = null
            }
        } catch (_: Exception) {}

        if (_connectionStatus.value == ConnectionStatus.SCANNING) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }

    override suspend fun startHost(roomName: String, maxPlayers: Int) {
        stopDiscovery()
        disconnect()

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        lobbyPlayerCount = maxPlayers
        _lobbyColors.value = recomputeLobbyColors()

        _connectionStatus.value = ConnectionStatus.ADVERTISING
        _connectedPeers.value = emptyList()

        withContext(Dispatchers.IO) {
            try {
                // Try insecure RFCOMM socket for immediate connection without pairing prompts, fallback to secure
                serverSocket = try {
                    bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, LUDO_APP_UUID)
                } catch (_: Exception) {
                    bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, LUDO_APP_UUID)
                }
                _connectionStatus.value = ConnectionStatus.CONNECTED_AS_HOST

                serverAcceptJob = transportScope.launch {
                    while (isActive && clientSockets.size < (maxPlayers - 1)) {
                        try {
                            val socket = serverSocket?.accept() ?: break
                            val address = socket.remoteDevice.address
                            val peerName = try { socket.remoteDevice.name ?: "Friend" } catch (_: Exception) { "Friend" }
                            clientSockets[address] = socket
                            peerNames[address] = peerName
                            _connectedPeers.value = clientSockets.keys.map { peerNames[it] ?: "Friend" }
                            _lobbyColors.value = recomputeLobbyColors()
                            startSocketReader(socket, isHost = true, identifier = address)

                            // Notify newly connected peer of current lobby state
                            val updateMsg = GameMessage.LobbySlotUpdate(
                                playerCount = maxPlayers,
                                hostName = getLocalDeviceName(),
                                connectedPeerNames = _connectedPeers.value,
                                colors = _lobbyColors.value
                            )
                            sendMessage(updateMsg)
                        } catch (e: IOException) {
                            break
                        }
                    }
                }

                startHeartbeat()
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    override suspend fun joinHost(deviceAddress: String) {
        stopDiscovery()
        disconnect()

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        _connectionStatus.value = ConnectionStatus.CONNECTING
        _connectedPeers.value = emptyList()

        withContext(Dispatchers.IO) {
            try {
                val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                // Always cancel discovery before connecting to prevent bandwidth contention
                try { bluetoothAdapter.cancelDiscovery() } catch (_: Exception) {}

                // Try insecure RFCOMM first, fallback to secure
                var socket: BluetoothSocket? = null
                try {
                    socket = device.createInsecureRfcommSocketToServiceRecord(LUDO_APP_UUID)
                    socket.connect()
                } catch (_: Exception) {
                    try { socket?.close() } catch (_: Exception) {}
                    socket = device.createRfcommSocketToServiceRecord(LUDO_APP_UUID)
                    socket.connect()
                }

                hostSocket = socket
                val hostName = try { device.name ?: "Host" } catch (_: Exception) { "Host" }
                _connectedPeers.value = listOf(hostName)
                _connectionStatus.value = ConnectionStatus.CONNECTED_AS_CLIENT
                startSocketReader(socket, isHost = false, identifier = "HOST")
                startHeartbeat()

                // Send LobbyJoin handshake with local device name
                val myName = getLocalDeviceName()
                sendMessage(GameMessage.LobbyJoin(playerId = "CLIENT", playerName = myName, requestedColor = null))
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _connectedPeers.value = emptyList()
            }
        }
    }

    private fun startSocketReader(socket: BluetoothSocket, isHost: Boolean, identifier: String) {
        val job = transportScope.launch {
            Log.d(TAG, "Socket reader started for $identifier (isHost=$isHost)")
            try {
                val input = DataInputStream(socket.inputStream)
                while (isActive) {
                    val length = input.readInt()
                    if (length <= 0 || length > 1024 * 1024) {
                        Log.w(TAG, "[$identifier] Invalid message length: $length. Breaking.")
                        break
                    }
                    val bytes = ByteArray(length)
                    input.readFully(bytes)
                    val rawJson = String(bytes, Charsets.UTF_8)
                    try {
                        val message = json.decodeFromString<GameMessage>(rawJson)
                        Log.d(TAG, "[$identifier] Received message: ${message::class.simpleName}")

                        // Dynamic Name & Color Handshake
                        if (isHost && message is GameMessage.LobbyJoin) {
                            peerNames[identifier] = message.playerName
                            if (message.requestedColor != null) {
                                peerRequestedColors[identifier] = message.requestedColor
                            }
                            _connectedPeers.value = clientSockets.keys.map { peerNames[it] ?: "Friend" }
                            _lobbyColors.value = recomputeLobbyColors()
                            Log.d(TAG, "Host recorded peer: ${message.playerName}, color: ${peerRequestedColors[identifier]}. All peers: ${_connectedPeers.value}, colors: ${_lobbyColors.value}")
                            // Broadcast updated lobby state to all clients
                            val slotUpdate = GameMessage.LobbySlotUpdate(
                                playerCount = lobbyPlayerCount,
                                hostName = getLocalDeviceName(),
                                connectedPeerNames = _connectedPeers.value,
                                colors = _lobbyColors.value
                            )
                            sendMessage(slotUpdate)
                        } else if (isHost && message is GameMessage.LobbyColorChange) {
                            if (lobbyPlayerCount == 4) {
                                peerRequestedColors[identifier] = message.newColor
                                _lobbyColors.value = recomputeLobbyColors()
                                val slotUpdate = GameMessage.LobbySlotUpdate(
                                    playerCount = lobbyPlayerCount,
                                    hostName = getLocalDeviceName(),
                                    connectedPeerNames = _connectedPeers.value,
                                    colors = _lobbyColors.value
                                )
                                sendMessage(slotUpdate)
                            }
                        } else if (!isHost && message is GameMessage.LobbySlotUpdate) {
                            val otherPeers = message.connectedPeerNames.filter { it != getLocalDeviceName() }
                            _connectedPeers.value = listOf(message.hostName) + otherPeers
                            if (message.colors.isNotEmpty()) {
                                _lobbyColors.value = message.colors
                            }
                            Log.d(TAG, "Client updated connected peers: ${_connectedPeers.value}, colors: ${_lobbyColors.value}")
                        }

                        _incomingMessages.emit(message)

                        // If we are host, broadcast non-private messages to all other clients
                        if (isHost && message !is GameMessage.Ping && message !is GameMessage.Pong) {
                            broadcastExcept(message, senderIdentifier = identifier)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[$identifier] Failed to decode message: ${e.message}", e)
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "[$identifier] Socket disconnected or read error: ${e.message}")
            } finally {
                handleDisconnection(identifier, isHost)
            }
        }
        readJobs[identifier] = job
    }

    private suspend fun broadcastExcept(message: GameMessage, senderIdentifier: String) {
        if (message is GameMessage.Ping || message is GameMessage.Pong) return
        val serialized = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        sendMutex.withLock {
            clientSockets.forEach { (id, socket) ->
                if (id != senderIdentifier && socket.isConnected) {
                    try {
                        val output = DataOutputStream(socket.outputStream)
                        output.writeInt(serialized.size)
                        output.write(serialized)
                        output.flush()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed broadcast to $id: ${e.message}")
                    }
                }
            }
        }
    }

    override suspend fun sendMessage(message: GameMessage) {
        val serialized = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        Log.d(TAG, "sendMessage: ${message::class.simpleName} (${serialized.size} bytes)")
        withContext(Dispatchers.IO) {
            sendMutex.withLock {
                when (_connectionStatus.value) {
                    ConnectionStatus.CONNECTED_AS_HOST -> {
                        clientSockets.values.forEach { socket ->
                            try {
                                if (socket.isConnected) {
                                    val output = DataOutputStream(socket.outputStream)
                                    output.writeInt(serialized.size)
                                    output.write(serialized)
                                    output.flush()
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Host failed to send to socket: ${e.message}")
                            }
                        }
                    }
                    ConnectionStatus.CONNECTED_AS_CLIENT -> {
                        hostSocket?.let { socket ->
                            try {
                                if (socket.isConnected) {
                                    val output = DataOutputStream(socket.outputStream)
                                    output.writeInt(serialized.size)
                                    output.write(serialized)
                                    output.flush()
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Client failed to send to host socket: ${e.message}")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = transportScope.launch {
            while (isActive) {
                delay(3000)
                try {
                    sendMessage(GameMessage.Ping(System.currentTimeMillis()))
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleDisconnection(identifier: String, isHost: Boolean) {
        if (isHost) {
            clientSockets.remove(identifier)?.let { try { it.close() } catch (_: Exception) {} }
            readJobs.remove(identifier)?.cancel()
            peerNames.remove(identifier)
            _connectedPeers.value = clientSockets.keys.map {
                peerNames[it] ?: "Friend"
            }
            transportScope.launch {
                _incomingMessages.emit(GameMessage.PlayerDisconnected(identifier))
            }
        } else {
            // We are client and lost connection to host
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _connectedPeers.value = emptyList()
            peerNames.clear()
            transportScope.launch { disconnect() }
        }
    }

    override suspend fun disconnect() {
        Log.d(TAG, "disconnect() called")
        stopDiscovery()
        heartbeatJob?.cancel()
        heartbeatJob = null
        serverAcceptJob?.cancel()
        serverAcceptJob = null
        readJobs.values.forEach { it.cancel() }
        readJobs.clear()
        _connectedPeers.value = emptyList()
        peerNames.clear()

        withContext(Dispatchers.IO) {
            sendMutex.withLock {
                try { serverSocket?.close() } catch (_: Exception) {}
                serverSocket = null

                clientSockets.values.forEach { try { it.close() } catch (_: Exception) {} }
                clientSockets.clear()

                try { hostSocket?.close() } catch (_: Exception) {}
                hostSocket = null
            }
        }

        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
}
