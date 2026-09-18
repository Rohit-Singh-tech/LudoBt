package com.ludobt.app.data.bluetooth

import com.ludobt.app.domain.model.GameMessage
import com.ludobt.app.domain.transport.ConnectionStatus
import com.ludobt.app.domain.transport.DiscoveredDevice
import com.ludobt.app.domain.transport.GameTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SimulatedBluetoothTransport : GameTransport {

    private val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<GameMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<GameMessage> = _incomingMessages.asSharedFlow()

    override suspend fun startDiscovery() {
        _connectionStatus.value = ConnectionStatus.SCANNING
        _discoveredDevices.value = emptyList()

        transportScope.launch {
            delay(1200)
            _discoveredDevices.value = listOf(
                DiscoveredDevice(name = "Rohit's Galaxy S24 (Host)", address = "SIM:BT:48:21:01", rssi = -45),
                DiscoveredDevice(name = "Amit's Pixel 9 Pro", address = "SIM:BT:48:21:02", rssi = -58),
                DiscoveredDevice(name = "Train Coach B4 - Ludo Room", address = "SIM:BT:48:21:03", rssi = -62)
            )
        }
    }

    override suspend fun stopDiscovery() {
        if (_connectionStatus.value == ConnectionStatus.SCANNING) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }

    override suspend fun startHost(roomName: String, maxPlayers: Int) {
        _connectionStatus.value = ConnectionStatus.CONNECTED_AS_HOST
    }

    override suspend fun joinHost(deviceAddress: String) {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        delay(1000)
        _connectionStatus.value = ConnectionStatus.CONNECTED_AS_CLIENT
    }

    override suspend fun sendMessage(message: GameMessage) {
        // Echo or process in simulated loopback
    }

    override suspend fun disconnect() {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _discoveredDevices.value = emptyList()
    }
}
