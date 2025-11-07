package com.example.mirroringapp.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mirroringapp.mirroring.ConnectionOption
import com.example.mirroringapp.util.PersistentLogger

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DeviceDiscoveryScreen(
    connectionType: ConnectionOption,
    context: Context,
    onBack: () -> Unit,
    onDeviceSelected: (String) -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }
    val displayManager = remember { context.getSystemService(DisplayManager::class.java) }
    val displays = remember(displayManager) { displayManager?.displays?.toList() ?: emptyList() }
    
    // Wireless device scanner
    val scanner = remember { com.example.mirroringapp.mirroring.WirelessDisplayScanner(context) }
    var wirelessDevices by remember { mutableStateOf<List<com.example.mirroringapp.mirroring.WirelessDisplayScanner.WirelessDevice>>(emptyList()) }
    
    // Scan for devices when scanning is enabled
    LaunchedEffect(isScanning, connectionType) {
        if (isScanning) {
            when (connectionType) {
                ConnectionOption.WIFI_DIRECT -> {
                    scanner.scanWifiDirectDevices().collect { devices ->
                        wirelessDevices = devices
                    }
                }
                ConnectionOption.MIRACAST -> {
                    scanner.scanMiracastDevices().collect { devices ->
                        wirelessDevices = devices
                    }
                }
                else -> {}
            }
        } else {
            wirelessDevices = emptyList()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when (connectionType) {
                            ConnectionOption.USB_C -> "USB-C Displays"
                            ConnectionOption.WIFI_DIRECT -> "WiFi Direct Devices"
                            ConnectionOption.MIRACAST -> "Miracast Displays"
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (connectionType != ConnectionOption.USB_C) {
                        IconButton(onClick = { isScanning = !isScanning }) {
                            Icon(
                                if (isScanning) Icons.Default.Close else Icons.Default.Refresh,
                                "Scan"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Connection Type Info Card
            item {
                ConnectionInfoCard(connectionType, context)
            }
            
            // Display/Device List
            when (connectionType) {
                ConnectionOption.USB_C -> {
                    item {
                        Text(
                            "Detected Displays",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    items(displays) { display ->
                        DisplayCard(display, context)
                    }
                    
                    if (displays.size == 1) {
                        item {
                            NoExternalDisplayCard()
                        }
                    }
                }
                
                ConnectionOption.WIFI_DIRECT -> {
                    item {
                        WiFiDirectDiscovery(
                            context = context,
                            isScanning = isScanning,
                            devices = wirelessDevices,
                            onDeviceSelected = { device ->
                                PersistentLogger.i("Device selected: ${device.name}")
                                onDeviceSelected(device.address)
                            }
                        )
                    }
                }
                
                ConnectionOption.MIRACAST -> {
                    item {
                        MiracastDiscovery(
                            context = context,
                            isScanning = isScanning,
                            devices = wirelessDevices,
                            onDeviceSelected = { device ->
                                PersistentLogger.i("Device selected: ${device.name}")
                                onDeviceSelected(device.address)
                            }
                        )
                    }
                }
            }
            
            // Log File Info
            item {
                Spacer(modifier = Modifier.height(16.dp))
                LogFileCard(context)
            }
        }
    }
}

@Composable
fun ConnectionInfoCard(connectionType: ConnectionOption, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (connectionType) {
                        ConnectionOption.USB_C -> "🔌"
                        ConnectionOption.WIFI_DIRECT -> "📡"
                        ConnectionOption.MIRACAST -> "📺"
                    },
                    fontSize = 32.sp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        connectionType.name.replace("_", " "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when (connectionType) {
                            ConnectionOption.USB_C -> "Direct wired connection"
                            ConnectionOption.WIFI_DIRECT -> "Peer-to-peer wireless"
                            ConnectionOption.MIRACAST -> "WiFi Display standard"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            
            when (connectionType) {
                ConnectionOption.USB_C -> {
                    InfoRow("Requirements", "USB-C cable with video support")
                    InfoRow("Latency", "Zero lag (direct rendering)")
                    InfoRow("Quality", "Native resolution")
                }
                ConnectionOption.WIFI_DIRECT -> {
                    val wifiManager = context.getSystemService(WifiManager::class.java)
                    val isWifiEnabled = wifiManager?.isWifiEnabled ?: false
                    InfoRow("WiFi Status", if (isWifiEnabled) "✅ Enabled" else "❌ Disabled")
                    InfoRow("Latency", "Low (hardware encoded)")
                    InfoRow("Quality", "High (H.264)")
                }
                ConnectionOption.MIRACAST -> {
                    val wifiManager = context.getSystemService(WifiManager::class.java)
                    val isWifiEnabled = wifiManager?.isWifiEnabled ?: false
                    InfoRow("WiFi Status", if (isWifiEnabled) "✅ Enabled" else "❌ Disabled")
                    InfoRow("Standard", "WiFi Display (Miracast)")
                    InfoRow("Compatibility", "Most modern TVs")
                }
            }
        }
    }
}

@Composable
fun DisplayCard(display: Display, context: Context) {
    val isExternal = display.displayId != Display.DEFAULT_DISPLAY
    
    PersistentLogger.d("Display detected: id=${display.displayId}, name=${display.name}, " +
            "size=${display.mode.physicalWidth}x${display.mode.physicalHeight}, external=$isExternal")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isExternal) 
                Color(0xFF4CAF50).copy(alpha = 0.1f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isExternal) "📺" else "📱",
                        fontSize = 24.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        display.name ?: "Display ${display.displayId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isExternal) Color(0xFF4CAF50) else Color(0xFF2196F3),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (isExternal) "EXTERNAL" else "BUILT-IN",
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow("Display ID", display.displayId.toString())
            InfoRow("Resolution", "${display.mode.physicalWidth} × ${display.mode.physicalHeight}")
            InfoRow("Refresh Rate", "${display.mode.refreshRate} Hz")
            InfoRow("State", getDisplayState(display.state))
            
            if (isExternal) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "✅ This display can be used for USB-C mirroring",
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun NoExternalDisplayCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "No External Display Detected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "To use USB-C mirroring:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("• Connect phone to TV via USB-C cable", fontSize = 11.sp)
            Text("• Ensure cable supports video (DisplayPort Alt Mode)", fontSize = 11.sp)
            Text("• Check TV input source is set to USB-C", fontSize = 11.sp)
            Text("• Verify phone supports video output over USB-C", fontSize = 11.sp)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            PersistentLogger.w("No external display detected for USB-C connection")
        }
    }
}

@Composable
fun WiFiDirectDiscovery(
    context: Context, 
    isScanning: Boolean,
    devices: List<com.example.mirroringapp.mirroring.WirelessDisplayScanner.WirelessDevice>,
    onDeviceSelected: (com.example.mirroringapp.mirroring.WirelessDisplayScanner.WirelessDevice) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "WiFi Direct Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    "WiFi Direct allows direct peer-to-peer connection between devices.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Setup Instructions:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("1. Enable WiFi on both devices", fontSize = 11.sp)
                Text("2. On TV: Enable WiFi Direct or Screen Mirroring", fontSize = 11.sp)
                Text("3. On Phone: Tap 'Scan' to discover devices", fontSize = 11.sp)
                Text("4. Select your TV from the list", fontSize = 11.sp)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (isScanning) {
                    PersistentLogger.i("WiFi Direct: Starting device discovery")
                    Text(
                        "🔍 Scanning for WiFi Direct devices...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else if (devices.isEmpty()) {
                    Text(
                        "Tap the refresh icon to scan for devices",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Show discovered devices
        if (devices.isNotEmpty()) {
            Text(
                "Found ${devices.size} device(s)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            devices.forEach { device ->
                WirelessDeviceCard(device, onDeviceSelected)
            }
        }
    }
}

@Composable
fun MiracastDiscovery(
    context: Context, 
    isScanning: Boolean,
    devices: List<com.example.mirroringapp.mirroring.WirelessDisplayScanner.WirelessDevice>,
    onDeviceSelected: (com.example.mirroringapp.mirroring.WirelessDisplayScanner.WirelessDevice) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Miracast Displays",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    "Miracast is a WiFi Display standard supported by most modern TVs.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Setup Instructions:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("1. On TV: Enable Screen Share/Miracast", fontSize = 11.sp)
                Text("   • LG: Settings → Network → Screen Share", fontSize = 11.sp)
                Text("   • Samsung: Settings → General → External Device Manager", fontSize = 11.sp)
                Text("   • Sony: Settings → Network → Wi-Fi Direct", fontSize = 11.sp)
                Text("2. On Phone: Tap 'Scan' to discover displays", fontSize = 11.sp)
                Text("3. Select your TV from the list", fontSize = 11.sp)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (isScanning) {
                    PersistentLogger.i("Miracast: Starting display discovery")
                    Text(
                        "🔍 Scanning for Miracast displays...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else if (devices.isEmpty()) {
                    Text(
                        "Tap the refresh icon to scan for displays",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Show discovered devices
        if (devices.isNotEmpty()) {
            Text(
                "Found ${devices.size} display(s)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            devices.forEach { device ->
                WirelessDeviceCard(device, onDeviceSelected)
            }
        }
    }
}

@Composable
fun WirelessDeviceCard(
    device: com.example.mirroringapp.mirroring.WirelessDisplayScanner.WirelessDevice,
    onDeviceSelected: (com.example.mirroringapp.mirroring.WirelessDisplayScanner.WirelessDevice) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceSelected(device) },
        colors = CardDefaults.cardColors(
            containerColor = if (device.isAvailable) 
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    device.address,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Status: ${device.status}",
                    fontSize = 11.sp,
                    color = if (device.isAvailable) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (device.isAvailable) {
                Text(
                    "✅",
                    fontSize = 24.sp
                )
            }
        }
    }
}

@Composable
fun LogFileCard(context: Context) {
    val logPath = PersistentLogger.getLogFilePath()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "📄",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Debug Log File",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "All diagnostics are saved to:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                logPath ?: "Not initialized",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "To retrieve logs:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "adb pull ${logPath ?: ""} .",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

fun getDisplayState(state: Int): String {
    return when (state) {
        Display.STATE_OFF -> "OFF"
        Display.STATE_ON -> "ON"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        else -> "UNKNOWN"
    }
}
