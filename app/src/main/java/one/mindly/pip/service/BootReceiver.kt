package one.mindly.pip.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import one.mindly.pip.R
import one.mindly.pip.appGraph
import one.mindly.pip.core.PIP_LOG_TAG

/**
 * Restarts listening after the phone reboots, if it was on before.
 *
 * Android 13 and lower allow starting a microphone foreground service right from
 * BOOT_COMPLETED. Android 14+ forbids it, so there we post a notification instead and
 * the user taps it to open Pip, which starts listening from the foreground.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!context.appGraph.settings.values.value.listeningEnabled) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.i(PIP_LOG_TAG, "[Boot] restarting listening")
            ListeningService.start(context)
        } else {
            Log.i(PIP_LOG_TAG, "[Boot] Android 14+: asking the user to tap to resume")
            postTapToResume(context)
        }
    }

    private fun postTapToResume(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ListeningService.createChannels(context)
        val notification = NotificationCompat.Builder(context, ListeningService.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_pip)
            .setContentTitle(context.getString(R.string.notif_boot_title))
            .setContentText(context.getString(R.string.notif_boot_text))
            .setContentIntent(ListeningService.openAppIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(BOOT_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val BOOT_NOTIFICATION_ID = 2
    }
}
