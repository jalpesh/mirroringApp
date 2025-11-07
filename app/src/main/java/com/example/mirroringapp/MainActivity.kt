package com.example.mirroringapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.core.content.ContextCompat
import com.example.mirroringapp.mirroring.ConnectionOption
import com.example.mirroringapp.ui.theme.MirroringAppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle USB accessory attachment (suppress system dialog)
        handleUsbAccessoryIntent(intent)

        setContent {
            MirroringAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel = hiltViewModel<MirroringViewModel>()
                    viewModel.initialize()
                    MirroringScreen(viewModel = viewModel)
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbAccessoryIntent(intent)
    }
    
    private fun handleUsbAccessoryIntent(intent: Intent?) {
        // Handle USB accessory attachment
        if (intent?.action == "android.hardware.usb.action.USB_ACCESSORY_ATTACHED") {
            // USB accessory detected - the app will handle display detection via DisplayManager
            // No additional action needed here as Presentation API handles it automatically
        }
    }
}

@Composable
fun MirroringScreen(viewModel: MirroringViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Permission state
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDeniedDialog by remember { mutableStateOf(false) }
    var missingPermissions by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Multiple permissions launcher
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            val denied = permissions.filterValues { !it }.keys.toList()
            missingPermissions = denied
            permissionDeniedDialog = true
        }
    }
    
    // Check permissions on launch
    LaunchedEffect(Unit) {
        val requiredPermissions = getRequiredPermissions()
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isNotEmpty()) {
            missingPermissions = missing
            showPermissionDialog = true
        }
    }
    
    // Permission request dialog
    if (showPermissionDialog) {
        PermissionRequestDialog(
            permissions = missingPermissions,
            onConfirm = {
                showPermissionDialog = false
                multiplePermissionsLauncher.launch(missingPermissions.toTypedArray())
            },
            onDismiss = {
                showPermissionDialog = false
                permissionDeniedDialog = true
            }
        )
    }
    
    // Permission denied dialog
    if (permissionDeniedDialog) {
        PermissionDeniedDialog(
            permissions = missingPermissions,
            onOpenSettings = {
                permissionDeniedDialog = false
                openAppSettings(context)
            },
            onDismiss = {
                permissionDeniedDialog = false
            }
        )
    }
    
    // Show error messages in snackbar
    LaunchedEffect(uiState) {
        if (uiState is MirroringUiState.Error) {
            val errorState = uiState as MirroringUiState.Error
            snackbarHostState.showSnackbar(
                message = errorState.message,
                actionLabel = if (errorState.canRetry) "Retry" else null
            )
        }
    }
    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { projectionData ->
                viewModel.startMirroring(result.resultCode, projectionData)
            } ?: viewModel.onProjectionDenied()
        } else {
            viewModel.onProjectionDenied()
        }
    }

    val foregroundServiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.requestProjectionPermission(projectionLauncher::launch)
        } else {
            viewModel.onProjectionDenied()
        }
    }

    // Debug info state
    var showDebugPanel by remember { mutableStateOf(true) }
    val displayManager = remember { context.getSystemService(DisplayManager::class.java) }
    val displays = remember(displayManager) { displayManager?.displays?.toList() ?: emptyList() }
    
    // Check hardware capabilities
    val capabilities = remember { 
        com.example.mirroringapp.util.HardwareCapabilityChecker.checkCapabilities(context)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Debug Panel Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Debug Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = showDebugPanel,
                    onCheckedChange = { showDebugPanel = it }
                )
            }
            
            // Hardware Capability Warning (if USB-C not supported)
            if (uiState.connectionOption == ConnectionOption.USB_C && 
                capabilities.usbCVideoOutput != com.example.mirroringapp.util.HardwareCapabilityChecker.UsbCCapability.SUPPORTED) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (capabilities.usbCVideoOutput == com.example.mirroringapp.util.HardwareCapabilityChecker.UsbCCapability.NOT_SUPPORTED)
                            Color(0xFFF44336).copy(alpha = 0.1f)
                        else
                            Color(0xFFFF9800).copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            if (capabilities.usbCVideoOutput == com.example.mirroringapp.util.HardwareCapabilityChecker.UsbCCapability.NOT_SUPPORTED)
                                "❌ USB-C Video Not Supported"
                            else
                                "⚠️ USB-C Video Unknown",
                            fontWeight = FontWeight.Bold,
                            color = if (capabilities.usbCVideoOutput == com.example.mirroringapp.util.HardwareCapabilityChecker.UsbCCapability.NOT_SUPPORTED)
                                Color(0xFFF44336)
                            else
                                Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            com.example.mirroringapp.util.HardwareCapabilityChecker.getUsbCExplanation(capabilities.usbCVideoOutput),
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Debug Panel
            if (showDebugPanel) {
                DebugPanel(
                    displays = displays,
                    uiState = uiState,
                    context = context
                )
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Show status message
            when (uiState) {
                is MirroringUiState.Mirroring -> {
                    Text(
                        text = "✓ Mirroring Active",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    (uiState as MirroringUiState.Mirroring).displayInfo?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is MirroringUiState.Starting, is MirroringUiState.RequestingPermission -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Starting mirroring...")
                }
                is MirroringUiState.Error -> {
                    Text(
                        text = "⚠ Error",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    Text(
                        text = "Ready to Mirror",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            val isEnabled = uiState !is MirroringUiState.Starting && 
                           uiState !is MirroringUiState.RequestingPermission
            
            Text(text = stringResource(id = R.string.latency_mode_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Switch(
                checked = uiState.lowLatencyEnabled,
                enabled = isEnabled,
                onCheckedChange = { viewModel.setLowLatencyEnabled(it) },
                modifier = Modifier.semantics {
                    contentDescription = "Low latency mode toggle"
                }
            )
            Text(
                text = stringResource(id = R.string.latency_mode_description),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(text = stringResource(id = R.string.use_hardware_encoder))
            Switch(
                checked = uiState.hardwareEncoderEnabled,
                enabled = isEnabled,
                onCheckedChange = { viewModel.setHardwareEncoderEnabled(it) },
                modifier = Modifier.semantics {
                    contentDescription = "Hardware encoder toggle"
                }
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "Connection Method:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            var showDiscoveryScreen by remember { mutableStateOf<ConnectionOption?>(null) }
            
            ConnectionOption.values().forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.setConnectionOption(option) },
                        enabled = isEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = "Select ${option.name} connection method"
                            }
                    ) {
                        val label = when (option) {
                            ConnectionOption.USB_C -> stringResource(id = R.string.usb_c_option)
                            ConnectionOption.WIFI_DIRECT -> stringResource(id = R.string.wifi_direct_option)
                            ConnectionOption.MIRACAST -> stringResource(id = R.string.miracast_option)
                        }
                        val selected = uiState.connectionOption == option
                        Text(text = if (selected) "✓ $label" else label)
                    }
                    
                    OutlinedButton(
                        onClick = { showDiscoveryScreen = option },
                        enabled = isEnabled,
                        modifier = Modifier.semantics {
                            contentDescription = "View ${option.name} devices"
                        }
                    ) {
                        Text("ℹ️")
                    }
                }
            }
            
            // Show discovery screen as dialog/overlay
            showDiscoveryScreen?.let { connectionType ->
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showDiscoveryScreen = null }
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        com.example.mirroringapp.ui.DeviceDiscoveryScreen(
                            connectionType = connectionType,
                            context = context,
                            onBack = { showDiscoveryScreen = null },
                            onDeviceSelected = { device ->
                                // Handle device selection
                                showDiscoveryScreen = null
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            // Main action button
            when (uiState) {
                is MirroringUiState.Mirroring -> {
                    Button(
                        onClick = { viewModel.stopMirroring() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Stop mirroring button"
                            }
                    ) {
                        Text(text = stringResource(id = R.string.stop_mirroring))
                    }
                }
                is MirroringUiState.Error -> {
                    if ((uiState as MirroringUiState.Error).canRetry) {
                        Button(
                            onClick = { viewModel.retryAfterError() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = "Retry mirroring button"
                                }
                        ) {
                            Text(text = "Retry")
                        }
                    }
                }
                is MirroringUiState.Starting, is MirroringUiState.RequestingPermission -> {
                    // Show disabled button while loading
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Starting...")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            // Check if all required permissions are granted
                            val allPermissionsGranted = getRequiredPermissions().all {
                                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                            }
                            
                            if (!allPermissionsGranted) {
                                val missing = getRequiredPermissions().filter {
                                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                }
                                missingPermissions = missing
                                showPermissionDialog = true
                            } else {
                                // Check foreground service permission separately (Android 9+)
                                val foregroundServiceGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.FOREGROUND_SERVICE
                                    ) == PackageManager.PERMISSION_GRANTED
                                } else true
                                
                                if (foregroundServiceGranted) {
                                    viewModel.requestProjectionPermission(projectionLauncher::launch)
                                } else {
                                    foregroundServiceLauncher.launch(Manifest.permission.FOREGROUND_SERVICE)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Start mirroring button"
                            }
                    ) {
                        Text(text = stringResource(id = R.string.start_mirroring))
                    }
                }
            }
        }
        
        // Snackbar for errors
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun DebugPanel(
    displays: List<Display>,
    uiState: MirroringUiState,
    context: android.content.Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Debug Info",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Connection Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Display Detection
            DebugSection("Display Detection") {
                DebugItem("Total Displays", displays.size.toString())
                
                displays.forEachIndexed { index, display ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val isExternal = display.displayId != Display.DEFAULT_DISPLAY
                    val displayType = if (isExternal) "EXTERNAL" else "BUILT-IN"
                    val statusColor = if (isExternal) Color(0xFF4CAF50) else Color(0xFF2196F3)
                    
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Display $index",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = statusColor.copy(alpha = 0.2f),
                                        shape = MaterialTheme.shapes.small
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = displayType,
                                    fontSize = 10.sp,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        DebugItem("  ID", display.displayId.toString())
                        DebugItem("  Name", display.name ?: "Unknown")
                        DebugItem("  Size", "${display.mode.physicalWidth}x${display.mode.physicalHeight}")
                        DebugItem("  Refresh", "${display.mode.refreshRate} Hz")
                        DebugItem("  State", getDisplayState(display.state))
                    }
                }
                
                if (displays.size == 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ No external display detected",
                        color = Color(0xFFFF9800),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "For USB-C: Connect phone to TV via USB-C cable",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Connection Status
            DebugSection("Connection Status") {
                val statusText = when (uiState) {
                    is MirroringUiState.Idle -> "Idle - Ready to start"
                    is MirroringUiState.RequestingPermission -> "Requesting screen capture permission"
                    is MirroringUiState.Starting -> "Starting mirroring session"
                    is MirroringUiState.Mirroring -> "✅ Mirroring Active"
                    is MirroringUiState.Error -> "❌ Error: ${(uiState as MirroringUiState.Error).message}"
                }
                val statusColor = when (uiState) {
                    is MirroringUiState.Mirroring -> Color(0xFF4CAF50)
                    is MirroringUiState.Error -> Color(0xFFF44336)
                    is MirroringUiState.Starting -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                DebugItem("Connection Type", uiState.connectionOption.name)
                DebugItem("Low Latency", if (uiState.lowLatencyEnabled) "Enabled" else "Disabled")
                DebugItem("HW Encoder", if (uiState.hardwareEncoderEnabled) "Enabled" else "Disabled")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // USB-C Diagnostics
            DebugSection("USB-C Diagnostics") {
                if (uiState.connectionOption == ConnectionOption.USB_C) {
                    val usbDisplayManager = remember { 
                        com.example.mirroringapp.mirroring.UsbDisplayManager(context)
                    }
                    val usbInfo = remember(displays) { 
                        usbDisplayManager.detectUsbDisplay()
                    }
                    
                    // Connection Type
                    val typeColor = when (usbInfo.type) {
                        com.example.mirroringapp.mirroring.UsbDisplayManager.UsbDisplayType.DISPLAYPORT_ALT_MODE -> Color(0xFF4CAF50)
                        com.example.mirroringapp.mirroring.UsbDisplayManager.UsbDisplayType.USB_ACCESSORY -> Color(0xFF2196F3)
                        com.example.mirroringapp.mirroring.UsbDisplayManager.UsbDisplayType.UNKNOWN -> Color(0xFFFF9800)
                        com.example.mirroringapp.mirroring.UsbDisplayManager.UsbDisplayType.NONE -> Color(0xFFF44336)
                    }
                    
                    val typeText = when (usbInfo.type) {
                        com.example.mirroringapp.mirroring.UsbDisplayManager.UsbDisplayType.DISPLAYPORT_ALT_MODE -> "✅ DisplayPort Alt Mode"
                        com.example.mirroringapp.mirroring.UsbDisplayManager.UsbDisplayType.USB_ACCESSORY -> "🔌 USB Accessory Mode"
                        com.example.mirroringapp.mirroring.UsbDisplayManager.UsbDisplayType.UNKNOWN -> "⚠️ Unknown Connection"
                        com.example.mirroringapp.mirroring.UsbDisplayManager.UsbDisplayType.NONE -> "❌ Not Connected"
                    }
                    
                    Text(
                        text = typeText,
                        color = typeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Status message
                    Text(
                        text = usbDisplayManager.getStatusMessage(usbInfo),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // USB Accessory details
                    if (usbInfo.usbAccessory != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Adapter Details:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        DebugItem("Manufacturer", usbInfo.usbAccessory.manufacturer ?: "Unknown")
                        DebugItem("Model", usbInfo.usbAccessory.model ?: "Unknown")
                    }
                } else {
                    Text(
                        text = "ℹ️ USB-C mode not selected",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Wireless Diagnostics
            DebugSection("Wireless Diagnostics") {
                val wifiManager = context.getSystemService(android.net.wifi.WifiManager::class.java)
                val isWifiEnabled = wifiManager?.isWifiEnabled ?: false
                val wifiInfo = wifiManager?.connectionInfo
                
                DebugItem("WiFi Status", if (isWifiEnabled) "Enabled" else "Disabled")
                if (isWifiEnabled && wifiInfo != null) {
                    DebugItem("Network", wifiInfo.ssid?.replace("\"", "") ?: "Unknown")
                    DebugItem("Signal", "${android.net.wifi.WifiManager.calculateSignalLevel(wifiInfo.rssi, 5)}/4")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (uiState.connectionOption == ConnectionOption.WIFI_DIRECT || 
                    uiState.connectionOption == ConnectionOption.MIRACAST) {
                    if (isWifiEnabled) {
                        Text(
                            text = "✅ WiFi enabled - Ready for wireless mirroring",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Make sure TV's Screen Share/Miracast is enabled",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "⚠️ WiFi is disabled",
                            color = Color(0xFFFF9800),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enable WiFi for wireless mirroring",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "ℹ️ Wireless mode not selected",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DebugSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
fun DebugItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
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

// Helper function to get required permissions
private fun getRequiredPermissions(): List<String> {
    val permissions = mutableListOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_NETWORK_STATE
    )
    
    // Add FOREGROUND_SERVICE_MEDIA_PROJECTION for Android 10+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        permissions.add(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION)
    }
    
    // Add POST_NOTIFICATIONS for Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    
    return permissions
}

// Helper function to open app settings
private fun openAppSettings(context: android.content.Context) {
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null)
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

// Permission request dialog
@Composable
private fun PermissionRequestDialog(
    permissions: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions Required") },
        text = {
            Column {
                Text("This app needs the following permissions to function properly:")
                Spacer(modifier = Modifier.height(12.dp))
                permissions.forEach { permission ->
                    Text(
                        text = "• ${getPermissionDescription(permission)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Grant Permissions")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Permission denied dialog
@Composable
private fun PermissionDeniedDialog(
    permissions: List<String>,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions Denied") },
        text = {
            Column {
                Text("The following permissions are required for the app to work:")
                Spacer(modifier = Modifier.height(12.dp))
                permissions.forEach { permission ->
                    Text(
                        text = "• ${getPermissionDescription(permission)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Please grant these permissions in Settings to use the app.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Get user-friendly permission descriptions
private fun getPermissionDescription(permission: String): String {
    return when (permission) {
        Manifest.permission.INTERNET -> "Internet access for wireless mirroring"
        Manifest.permission.ACCESS_WIFI_STATE -> "Check WiFi connection status"
        Manifest.permission.CHANGE_WIFI_STATE -> "Manage WiFi connections"
        Manifest.permission.ACCESS_NETWORK_STATE -> "Check network connectivity"
        Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION -> "Screen capture service"
        Manifest.permission.POST_NOTIFICATIONS -> "Show mirroring notifications"
        Manifest.permission.FOREGROUND_SERVICE -> "Run mirroring in background"
        else -> permission.substringAfterLast(".")
    }
}
