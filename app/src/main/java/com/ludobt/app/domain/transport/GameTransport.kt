package com.ludobt.app.domain.transport

import com.ludobt.app.domain.model.GameMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionStatus {
    DISCONNECTED,
    SCANNING,
    ADVERTISING,
    CONNECTING,
    CONNECTED_AS_HOST,
    CONNECTED_AS_CLIENT
}

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int = -60
)

interface GameTransport {
    val connectionStatus: StateFlow<ConnectionStatus>
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>
    val incomingMessages: Flow<GameMessage>

    val connectedPeers: StateFlow<List<String>> get() = kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    fun getLocalDeviceName(): String = "Android Device"
    fun getLocalPlayerId(): String = ""

    suspend fun startDiscovery()
    suspend fun stopDiscovery()
    suspend fun startHost(roomName: String, maxPlayers: Int = 4)
    suspend fun joinHost(deviceAddress: String)
    suspend fun sendMessage(message: GameMessage)
    suspend fun disconnect()
}
