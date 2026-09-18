package com.ludobt.app.data.network

import android.util.Log
import com.ludobt.app.domain.model.GameMessage
import com.ludobt.app.domain.model.Player
import com.ludobt.app.domain.model.PlayerColor
import com.ludobt.app.domain.transport.ConnectionStatus
import com.ludobt.app.domain.transport.DiscoveredDevice
import com.ludobt.app.domain.transport.GameTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Real-Time WebSocket Game Transport for Online Multiplayer.
 * Connects to the authoritative Ludo game server.
 */
class WebSocketGameTransport(
    private var serverUrl: String = "ws://10.0.2.2:8080"
) : GameTransport {

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

    private var activeWebSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    override fun getLocalDeviceName(): String = "Online Player"

    fun setServerUrl(url: String) {
        serverUrl = url.trim()
    }

    fun getServerUrl(): String = serverUrl

    suspend fun connect(url: String = serverUrl) {
        if (activeWebSocket != null && _connectionStatus.value != ConnectionStatus.DISCONNECTED) {
            return
        }

        _connectionStatus.value = ConnectionStatus.CONNECTING

        val request = Request.Builder()
            .url(url)
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketTransport", "Connected to game server at $url")
                _connectionStatus.value = ConnectionStatus.CONNECTED_AS_CLIENT
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = json.decodeFromString(GameMessage.serializer(), text)
                    when (msg) {
                        is GameMessage.RoomCreatedEvent -> {
                            _currentRoomPin.value = msg.roomPin
                            _connectionStatus.value = ConnectionStatus.CONNECTED_AS_HOST
                        }
                        is GameMessage.LobbyUpdate -> {
                            _lobbyPlayers.value = msg.players
                            _connectedPeers.value = msg.players.map { it.name }
                        }
                        is GameMessage.ErrorMessage -> {
                            _lastError.tryEmit(msg.message)
                        }
                        else -> {}
                    }
                    _incomingMessages.tryEmit(msg)
                } catch (e: Exception) {
                    Log.e("WebSocketTransport", "Failed to deserialize message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocketTransport", "WebSocket connection closed: $reason")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                activeWebSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocketTransport", "WebSocket connection failure", t)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _lastError.tryEmit("Server connection error: ${t.localizedMessage ?: "Unable to connect"}")
                activeWebSocket = null
            }
        })
    }

    suspend fun createRoom(playerCount: Int, playerName: String, color: PlayerColor) {
        ensureConnected()
        sendMessage(GameMessage.CreateRoomRequest(playerCount = playerCount, playerName = playerName, color = color))
    }

    suspend fun joinRoom(roomPin: String, playerName: String, color: PlayerColor) {
        ensureConnected()
        sendMessage(GameMessage.JoinRoomRequest(roomPin = roomPin, playerName = playerName, color = color))
    }

    suspend fun quickMatch(playerCount: Int, playerName: String, color: PlayerColor) {
        ensureConnected()
        sendMessage(GameMessage.QuickMatchRequest(playerCount = playerCount, playerName = playerName, color = color))
    }

    private suspend fun ensureConnected() {
        if (activeWebSocket == null || _connectionStatus.value == ConnectionStatus.DISCONNECTED) {
            connect()
            // Short grace period to wait for socket handshake
            var waited = 0
            while (_connectionStatus.value == ConnectionStatus.CONNECTING && waited < 30) {
                kotlinx.coroutines.delay(100)
                waited++
            }
        }
    }

    override suspend fun sendMessage(message: GameMessage) {
        try {
            val jsonText = json.encodeToString(GameMessage.serializer(), message)
            activeWebSocket?.send(jsonText)
        } catch (e: Exception) {
            Log.e("WebSocketTransport", "Error sending message", e)
        }
    }

    override suspend fun startDiscovery() {
        // No-op for WebSocket
    }

    override suspend fun stopDiscovery() {
        // No-op for WebSocket
    }

    override suspend fun startHost(roomName: String, maxPlayers: Int) {
        createRoom(maxPlayers, "Host", PlayerColor.RED)
    }

    override suspend fun joinHost(deviceAddress: String) {
        joinRoom(deviceAddress, "Guest", PlayerColor.BLUE)
    }

    override suspend fun disconnect() {
        try {
            activeWebSocket?.close(1000, "User Left")
        } catch (_: Exception) {}
        activeWebSocket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _currentRoomPin.value = null
        _lobbyPlayers.value = emptyList()
    }
}
