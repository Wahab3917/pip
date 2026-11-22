package one.mindly.pip.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.mindly.pip.MainActivity
import one.mindly.pip.R
import one.mindly.pip.appGraph
import one.mindly.pip.core.PIP_LOG_TAG
import one.mindly.pip.core.audio.Microphone
import one.mindly.pip.core.capture.EngineStatus
import one.mindly.pip.core.capture.ListeningEngine
import one.mindly.pip.core.feedback.CuePlayer
import one.mindly.pip.ui.statusText
import java.util.Date

/**
 * Keeps Pip listening with the screen off and the phone locked, and runs one-off catches
 * from the quick-settings tile and the widget.
 *
 * A *foreground* service of type "microphone" is the only way Android lets an app keep using
 * the mic in the background, and it requires a visible notification. Ours is low-priority
 * (no sound, small icon) and carries the "Pause for 1 hour" action.
 *
 * The service runs while "Hey Pip" is on, or while a one-off catch is in progress; when the
 * engine has nothing left to do it stops itself.
 *
 * This class is only Android glue: the actual listening logic is core/capture/ListeningEngine.
 */
class ListeningService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: ListeningEngine? = null
    /** The latest start request; see stopSelfResult() below. */
    private var lastStartId = 0
    private lateinit var cues: CuePlayer

    override fun onCreate() {
        super.onCreate()
        cues = CuePlayer(this)
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val settings = appGraph.settings
        when (intent?.action) {
            ACTION_PAUSE_HOUR -> settings.pauseFor(ONE_HOUR_MS)
            ACTION_RESUME -> settings.resume()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(PIP_LOG_TAG, "[Service] no RECORD_AUDIO permission, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        // Must be called within a few seconds of startForegroundService(), every time.
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(appGraph.engineStatus.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (e: Exception) {
            // Android 14+ refuses a mic foreground service started from the background.
            Log.e(PIP_LOG_TAG, "[Service] not allowed to start in foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }

        val engine = engine ?: startEngine()
        when (intent?.action) {
            ACTION_CATCH -> engine.requestCatch()
            ACTION_FINISH -> engine.requestFinish()
            ACTION_CANCEL -> engine.requestCancel()
        }
        // START_STICKY: if Android kills us for memory, it recreates the service later.
        return START_STICKY
    }

    private fun startEngine(): ListeningEngine {
        val graph = appGraph
        val engine = ListeningEngine(
            context = this,
            settings = graph.settings,
            cues = cues,
            status = graph.engineStatus,
            level = graph.captureLevel,
            saveThought = { pcm -> graph.repository.saveCaught(pcm, Microphone.SAMPLE_RATE) },
        )
        this.engine = engine
        scope.launch {
            try {
                engine.run()
            } catch (e: SecurityException) {
                Log.e(PIP_LOG_TAG, "[Service] mic permission revoked", e)
            }
            // run() returned: "Hey Pip" is off and no catch is pending, so there's nothing to do.
            // A new request from here on starts a fresh engine.
            this@ListeningService.engine = null
            // The caught/missed cue has only just started; stopping now would release the sound
            // player and cut it off. Let it play out first.
            delay(CUE_TAIL_MS)
            // stopSelfResult() only stops if no newer start request (e.g. another catch) arrived.
            stopSelfResult(lastStartId)
        }
        // Keep the notification text in sync with what the engine is doing.
        scope.launch {
            graph.engineStatus.collect { status ->
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(status))
            }
        }
        Log.i(PIP_LOG_TAG, "[Service] started")
        return engine
    }

    override fun onDestroy() {
        scope.cancel()
        cues.release()
        appGraph.engineStatus.value = EngineStatus.Off
        Log.i(PIP_LOG_TAG, "[Service] stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(status: EngineStatus): Notification {
        val paused = status as? EngineStatus.Paused
        val title = getString(
            when {
                paused != null -> R.string.notif_paused_title
                status == EngineStatus.Capturing -> R.string.notif_catching_title
                else -> R.string.notif_listening_title
            }
        )
        val text = when {
            paused?.untilMs != null -> getString(
                R.string.notif_paused_until,
                DateFormat.getTimeFormat(this).format(Date(paused.untilMs)),
            )
            status == EngineStatus.Listening -> getString(R.string.notif_listening_text)
            else -> getString(status.statusText())
        }
        val userPaused = paused?.untilMs != null
        val action = if (userPaused) {
            NotificationCompat.Action(0, getString(R.string.notif_action_resume), serviceIntent(ACTION_RESUME))
        } else {
            NotificationCompat.Action(0, getString(R.string.notif_action_pause_hour), serviceIntent(ACTION_PAUSE_HOUR))
        }
        return NotificationCompat.Builder(this, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_stat_pip)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(this))
            .addAction(action)
            .build()
    }

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, ListeningService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val NOTIFICATION_ID = 1
        const val CHANNEL_LISTENING = "listening"
        const val CHANNEL_ALERTS = "alerts"
        private const val ACTION_PAUSE_HOUR = "one.mindly.pip.PAUSE_HOUR"
        private const val ACTION_RESUME = "one.mindly.pip.RESUME"
        private const val ACTION_CATCH = "one.mindly.pip.CATCH"
        private const val ACTION_FINISH = "one.mindly.pip.FINISH"
        private const val ACTION_CANCEL = "one.mindly.pip.CANCEL"
        /** Longer than the longest cue (cue_caught is ~0.45 s). */
        private const val CUE_TAIL_MS = 800L
        private const val ONE_HOUR_MS = 60 * 60 * 1000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ListeningService::class.java))
        }

        /**
         * Records one thought now, without the wake word. Call it only while Pip is in the
         * foreground (an activity is showing): Android lets a service use the mic only if it
         * was started from the foreground. QuickActionActivity takes care of that.
         */
        fun catchNow(context: Context) {
            context.startForegroundService(Intent(context, ListeningService::class.java).setAction(ACTION_CATCH))
        }

        /** "Tap to finish" in the capture overlay: end the note now and save what was said. */
        fun finishCatch(context: Context) {
            context.startService(Intent(context, ListeningService::class.java).setAction(ACTION_FINISH))
        }

        /** The capture overlay's ✕: throw the current note away. */
        fun cancelCatch(context: Context) {
            context.startService(Intent(context, ListeningService::class.java).setAction(ACTION_CANCEL))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListeningService::class.java))
        }

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_LISTENING,
                    context.getString(R.string.channel_listening_name),
                    NotificationManager.IMPORTANCE_LOW, // No sound, no pop-up.
                ).apply { description = context.getString(R.string.channel_listening_description) }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    context.getString(R.string.channel_alerts_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }

        fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
