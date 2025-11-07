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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mirroringapp.mirroring.ConnectionOption
import com.example.mirroringapp.ui.theme.MirroringAppTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
}

@Composable
fun MirroringScreen(viewModel: MirroringViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.requestProjectionPermission(projectionLauncher::launch)
        } else {
            viewModel.onProjectionDenied()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
            ConnectionOption.values().forEach { option ->
                Button(
                    onClick = { viewModel.setConnectionOption(option) },
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
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
                            val permissionStatus = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.FOREGROUND_SERVICE
                            )
                            if (permissionStatus == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                                viewModel.requestProjectionPermission(projectionLauncher::launch)
                            } else {
                                permissionLauncher.launch(Manifest.permission.FOREGROUND_SERVICE)
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
