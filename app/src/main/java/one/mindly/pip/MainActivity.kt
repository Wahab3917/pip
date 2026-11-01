package one.mindly.pip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.mindly.pip.service.ListeningService
import one.mindly.pip.ui.home.HomeScreen
import one.mindly.pip.ui.home.HomeViewModel
import one.mindly.pip.ui.home.PermissionState
import one.mindly.pip.ui.theme.PipTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PipTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var permissions by remember { mutableStateOf(readPermissions(justAsked = false)) }
                // Remember that the user flipped the switch, so we can start once they grant the mic.
                var startWhenGranted by remember { mutableStateOf(false) }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    permissions = readPermissions(justAsked = true)
                    if (startWhenGranted && permissions.micGranted) viewModel.setListening(true)
                    startWhenGranted = false
                }
                val requestPermissions = { launcher.launch(permissionsToRequest()) }

                // Coming back from system Settings: the user may have changed a permission there.
                LifecycleResumeEffect(Unit) {
                    permissions = readPermissions(justAsked = false).copy(
                        micPermanentlyDenied = permissions.micPermanentlyDenied && !hasMic(),
                    )
                    onPauseOrDispose {}
                }

                HomeScreen(
                    state = state,
                    permissions = permissions,
                    onListeningChange = { enabled ->
                        if (enabled && !permissions.micGranted) {
                            startWhenGranted = true
                            requestPermissions()
                        } else {
                            viewModel.setListening(enabled)
                        }
                    },
                    onSensitivityChange = viewModel::setSensitivity,
                    onPauseInBatterySaverChange = viewModel::setPauseInBatterySaver,
                    onThoughtClick = viewModel::togglePlayback,
                    onRequestMic = requestPermissions,
                    onOpenSettings = ::openAppSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If listening is on but the service isn't running (app updated, killed, or rebooted on
        // Android 14+), start it now. We're in the foreground, which Android requires for the mic.
        // Calling start() when it's already running is harmless.
        if (appGraph.settings.values.value.listeningEnabled && hasMic()) {
            ListeningService.start(this)
        }
    }

    private fun hasMic() = isGranted(Manifest.permission.RECORD_AUDIO)

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Android only tells us "permanently denied" indirectly: after a request was refused,
     * shouldShowRequestPermissionRationale() is false when the system won't ask again.
     */
    private fun readPermissions(justAsked: Boolean): PermissionState {
        val mic = hasMic()
        return PermissionState(
            micGranted = mic,
            micPermanentlyDenied = justAsked && !mic &&
                !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
            notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                isGranted(Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    private fun permissionsToRequest(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }
}
