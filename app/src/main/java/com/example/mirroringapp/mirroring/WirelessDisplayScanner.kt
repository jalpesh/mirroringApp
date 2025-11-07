package com.example.mirroringapp.mirroring

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.mirroringapp.util.PersistentLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/**
 * Scans for wireless display devices (WiFi Direct and Miracast).
 */
class WirelessDisplayScanner(private val context: Context) {
    
    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    
    private val wifiManager: WifiManager by lazy {
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    
    private var channel: WifiP2pManager.Channel? = null
    
    data class WirelessDevice(
        val name: String,
        val address: String,
        val type: DeviceType,
        val status: String,
        val isAvailable: Boolean
    )
    
    enum class DeviceType {
        WIFI_DIRECT,
        MIRACAST,
        UNKNOWN
    }
    
    /**
     * Check if WiFi is enabled.
     */
    fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }
    
    /**
     * Check if location permission is granted (required for WiFi Direct on Android 9+).
     */
    fun hasLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * Scan for WiFi Direct devices.
     * Returns a Flow that emits device lists as they're discovered.
     */
    fun scanWifiDirectDevices(): Flow<List<WirelessDevice>> = callbackFlow {
        Timber.i("Starting WiFi Direct device scan")
        PersistentLogger.i("Starting WiFi Direct device scan")
        
        if (!isWifiEnabled()) {
            Timber.w("WiFi is disabled - cannot scan")
            PersistentLogger.w("WiFi is disabled - cannot scan")
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        if (!hasLocationPermission()) {
            Timber.w("Location permission not granted - cannot scan")
            PersistentLogger.w("Location permission not granted - cannot scan")
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        // Initialize channel
        channel = wifiP2pManager?.initialize(context, context.mainLooper, null)
        
        if (channel == null) {
            Timber.e("Failed to initialize WiFi P2P channel")
            PersistentLogger.e("Failed to initialize WiFi P2P channel")
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        // Peer list listener
        val peerListListener = WifiP2pManager.PeerListListener { peerList: WifiP2pDeviceList ->
            val devices = peerList.deviceList.map { device ->
                val statusText = when (device.status) {
                    WifiP2pDevice.AVAILABLE -> "Available"
                    WifiP2pDevice.INVITED -> "Invited"
                    WifiP2pDevice.CONNECTED -> "Connected"
                    WifiP2pDevice.FAILED -> "Failed"
                    WifiP2pDevice.UNAVAILABLE -> "Unavailable"
                    else -> "Unknown"
                }
                
                Timber.d("Found device: ${device.deviceName} (${device.deviceAddress}) - $statusText")
                PersistentLogger.i("Found WiFi Direct device: ${device.deviceName} - $statusText")
                
                WirelessDevice(
                    name = device.deviceName,
                    address = device.deviceAddress,
                    type = DeviceType.WIFI_DIRECT,
                    status = statusText,
                    isAvailable = device.status == WifiP2pDevice.AVAILABLE
                )
            }
            
            Timber.i("WiFi Direct scan found ${devices.size} devices")
            PersistentLogger.i("WiFi Direct scan found ${devices.size} devices")
            trySend(devices)
        }
        
        // Broadcast receiver for WiFi P2P state changes
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        Timber.d("WiFi P2P state changed: ${if (enabled) "enabled" else "disabled"}")
                    }
                    
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        Timber.d("WiFi P2P peers changed - requesting peer list")
                        wifiP2pManager?.requestPeers(channel, peerListListener)
                    }
                    
                    WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
                        val discovering = state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                        Timber.d("WiFi P2P discovery: ${if (discovering) "started" else "stopped"}")
                        PersistentLogger.i("WiFi P2P discovery: ${if (discovering) "started" else "stopped"}")
                    }
                }
            }
        }
        
        // Register receiver
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, intentFilter)
        
        // Start discovery
        try {
            wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Timber.i("✅ WiFi Direct discovery started successfully")
                    PersistentLogger.i("✅ WiFi Direct discovery started successfully")
                }
                
                override fun onFailure(reason: Int) {
                    val reasonText = when (reason) {
                        WifiP2pManager.P2P_UNSUPPORTED -> "P2P unsupported"
                        WifiP2pManager.BUSY -> "Busy"
                        WifiP2pManager.ERROR -> "Error"
                        else -> "Unknown ($reason)"
                    }
                    Timber.e("❌ WiFi Direct discovery failed: $reasonText")
                    PersistentLogger.e("❌ WiFi Direct discovery failed: $reasonText")
                    trySend(emptyList())
                }
            })
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception during WiFi Direct discovery")
            PersistentLogger.e("Security exception during WiFi Direct discovery: ${e.message}")
            trySend(emptyList())
        }
        
        awaitClose {
            Timber.i("Stopping WiFi Direct scan")
            PersistentLogger.i("Stopping WiFi Direct scan")
            try {
                context.unregisterReceiver(receiver)
                wifiP2pManager?.stopPeerDiscovery(channel, null)
            } catch (e: Exception) {
                Timber.e(e, "Error stopping WiFi Direct scan")
            }
        }
    }
    
    /**
     * Scan for Miracast/WiFi Display devices.
     * Note: Android doesn't provide a direct API for Miracast device discovery.
     * This uses WiFi Direct as Miracast is built on top of it.
     */
    fun scanMiracastDevices(): Flow<List<WirelessDevice>> = callbackFlow {
        Timber.i("Starting Miracast device scan (using WiFi Direct)")
        PersistentLogger.i("Starting Miracast device scan")
        
        // Miracast uses WiFi Direct under the hood
        // Filter for devices that are likely displays/TVs
        scanWifiDirectDevices().collect { devices ->
            val miracastDevices = devices.filter { device ->
                // Look for common TV/display keywords in device name
                val name = device.name.lowercase()
                name.contains("tv") ||
                name.contains("display") ||
                name.contains("screen") ||
                name.contains("cast") ||
                name.contains("miracast") ||
                name.contains("lg") ||
                name.contains("samsung") ||
                name.contains("sony") ||
                name.contains("roku")
            }.map { it.copy(type = DeviceType.MIRACAST) }
            
            Timber.i("Found ${miracastDevices.size} potential Miracast devices")
            PersistentLogger.i("Found ${miracastDevices.size} potential Miracast devices")
            trySend(miracastDevices)
        }
        
        awaitClose {
            Timber.i("Stopping Miracast scan")
        }
    }
    
    /**
     * Connect to a WiFi Direct device.
     */
    fun connectToDevice(device: WirelessDevice, callback: (Boolean, String) -> Unit) {
        Timber.i("Attempting to connect to device: ${device.name}")
        PersistentLogger.i("Attempting to connect to device: ${device.name} (${device.address})")
        
        // This would require implementing the full WiFi Direct connection flow
        // For now, just log that it was attempted
        Timber.w("Device connection not fully implemented yet")
        PersistentLogger.w("Device connection requires additional implementation")
        callback(false, "Connection feature coming soon")
    }
    
    /**
     * Get WiFi network info.
     */
    fun getWifiInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        
        try {
            val wifiInfo = wifiManager.connectionInfo
            info["SSID"] = wifiInfo.ssid.replace("\"", "")
            info["Signal Strength"] = "${WifiManager.calculateSignalLevel(wifiInfo.rssi, 5)}/5"
            info["Link Speed"] = "${wifiInfo.linkSpeed} Mbps"
            info["IP Address"] = intToIp(wifiInfo.ipAddress)
        } catch (e: Exception) {
            Timber.e(e, "Error getting WiFi info")
        }
        
        return info
    }
    
    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}
