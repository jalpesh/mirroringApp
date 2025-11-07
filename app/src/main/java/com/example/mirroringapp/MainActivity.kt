package com.example.mirroringapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.mirroringapp.mirroring.ConnectionOption
import com.example.mirroringapp.ui.theme.MirroringAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MirroringViewModel by viewModels {
        MirroringViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.ensureInitialisation()
        }

        setContent {
            MirroringAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val projectionData = result.data
            scope.launch {
                viewModel.startMirroring(result.resultCode, projectionData)
            }
        } else {
            viewModel.onProjectionDenied()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch { viewModel.requestProjectionPermission(projectionLauncher::launch) }
        } else {
            viewModel.onProjectionDenied()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(id = R.string.latency_mode_title), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Switch(
            checked = uiState.lowLatencyEnabled,
            onCheckedChange = {
                scope.launch { viewModel.setLowLatencyEnabled(it) }
            }
        )
        Text(text = stringResource(id = R.string.latency_mode_description), modifier = Modifier.padding(top = 8.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = stringResource(id = R.string.use_hardware_encoder))
        Switch(
            checked = uiState.hardwareEncoderEnabled,
            onCheckedChange = {
                scope.launch { viewModel.setHardwareEncoderEnabled(it) }
            }
        )
        Spacer(modifier = Modifier.height(24.dp))

        ConnectionOption.values().forEach { option ->
            val isSelected = uiState.connectionOption == option
            Button(
                onClick = {
                    scope.launch { viewModel.setConnectionOption(option) }
                },
                modifier = Modifier.padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(text = when (option) {
                    ConnectionOption.USB_C -> stringResource(id = R.string.usb_c_option)
                    ConnectionOption.WIFI_DIRECT -> stringResource(id = R.string.wifi_direct_option)
                    ConnectionOption.MIRACAST -> stringResource(id = R.string.miracast_option)
                })
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        val startStopLabel = if (uiState.isMirroring) R.string.stop_mirroring else R.string.start_mirroring
        Button(onClick = {
            scope.launch {
                if (uiState.isMirroring) {
                    viewModel.stopMirroring()
                } else {
                    val permissionStatus = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.FOREGROUND_SERVICE
                    )
                    if (permissionStatus == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                        viewModel.requestProjectionPermission(projectionLauncher::launch)
                    } else {
                        permissionLauncher.launch(Manifest.permission.FOREGROUND_SERVICE)
                    }
                }
            }
        }) {
            Text(text = stringResource(id = startStopLabel))
        }
    }
}
