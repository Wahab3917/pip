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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.mindly.pip.service.ListeningService
import one.mindly.pip.ui.home.HomeScreen
import one.mindly.pip.ui.home.HomeViewModel
import one.mindly.pip.ui.home.PermissionState
import one.mindly.pip.ui.note.EditScreen
import one.mindly.pip.ui.note.NoteSheet
import one.mindly.pip.ui.settings.SettingsScreen
import one.mindly.pip.ui.capture.CaptureOverlay
import one.mindly.pip.ui.theme.PipMotion
import one.mindly.pip.core.capture.EngineStatus
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import one.mindly.pip.ui.theme.PipTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PipTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var permissions by remember { mutableStateOf(readPermissions(justAsked = false)) }
                // What to do once the user grants the mic: turn on "Hey Pip", or catch a thought.
                var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    permissions = readPermissions(justAsked = true)
                    if (permissions.micGranted) afterGrant?.invoke()
                    afterGrant = null
                }
                val requestPermissions = { launcher.launch(permissionsToRequest()) }
                fun withMic(action: () -> Unit) {
                    if (permissions.micGranted) action() else { afterGrant = action; requestPermissions() }
                }
                val setListening = { enabled: Boolean ->
                    if (enabled) withMic { viewModel.setListening(true) } else viewModel.setListening(false)
                }

                // Coming back from system Settings: the user may have changed a permission there.
                LifecycleResumeEffect(Unit) {
                    permissions = readPermissions(justAsked = false).copy(
                        micPermanentlyDenied = permissions.micPermanentlyDenied && !hasMic(),
                    )
                    onPauseOrDispose {}
                }

                // Few screens, so no navigation library. Where we are: Settings, the editor for a
                // thought, or the Pocket with (maybe) one thought open in the bottom sheet.
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var editingId by rememberSaveable { mutableStateOf<String?>(null) }
                var openId by rememberSaveable { mutableStateOf<String?>(null) }

                val editing = editingId?.let { viewModel.thought(UUID.fromString(it)).collectAsStateWithLifecycle(null).value }
                val open = openId?.let { viewModel.thought(UUID.fromString(it)).collectAsStateWithLifecycle(null).value }
                val closeSheet = {
                    viewModel.stopPlayback()
                    openId = null
                }

                when {
                    showSettings -> SettingsScreen(
                        settings = state.settings,
                        engineStatus = state.engineStatus,
                        showNotificationHint = permissions.micGranted && !permissions.notificationsGranted,
                        onListeningChange = setListening,
                        onSensitivityChange = viewModel::setSensitivity,
                        onPauseInBatterySaverChange = viewModel::setPauseInBatterySaver,
                        onBack = { showSettings = false },
                    )
                    editing != null -> EditScreen(
                        initialText = editing.transcript.orEmpty(),
                        onSave = {
                            viewModel.editTranscript(editing.id, it)
                            editingId = null
                        },
                        onCancel = { editingId = null },
                    )
                    else -> {
                        HomeScreen(
                            state = state,
                            permissions = permissions,
                            onToggleListening = { setListening(!state.settings.listeningEnabled) },
                            onCatch = { withMic(viewModel::catchNow) },
                            onOpenSettings = { showSettings = true },
                            onThoughtClick = { openId = it.id.toString() },
                            onPlay = viewModel::togglePlayback,
                            onDeleteThoughts = viewModel::deleteAll,
                            onRequestMic = requestPermissions,
                            onOpenAppSettings = ::openAppSettings,
                        )
                        if (open != null) {
                            val player by viewModel.playerState.collectAsStateWithLifecycle()
                            NoteSheet(
                                thought = open,
                                player = player,
                                positionMs = viewModel::playbackPositionMs,
                                onTogglePlay = { viewModel.togglePlayback(open) },
                                onSeek = { viewModel.seek(open, it) },
                                onRetry = { viewModel.retryTranscription(open.id) },
                                onEdit = {
                                    viewModel.stopPlayback()
                                    editingId = open.id.toString()
                                },
                                onDelete = {
                                    viewModel.delete(open)
                                    closeSheet()
                                },
                                onDismiss = closeSheet,
                            )
                        }
                    }
                }

                // Over everything while Pip is catching a thought, whatever started it.
                AnimatedVisibility(
                    visible = state.engineStatus == EngineStatus.Capturing,
                    enter = fadeIn(tween(PipMotion.MEDIUM_MS)),
                    // "Caught: the seed drops into the pocket in about 200 ms."
                    exit = fadeOut(tween(PipMotion.MEDIUM_MS)) + scaleOut(tween(PipMotion.MEDIUM_MS), targetScale = 0.85f),
                ) {
                    val level by viewModel.captureLevel.collectAsStateWithLifecycle()
                    CaptureOverlay(level = level, onFinish = viewModel::finishCatch)
                }
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
