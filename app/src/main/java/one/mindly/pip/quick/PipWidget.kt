package one.mindly.pip.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import one.mindly.pip.MainActivity
import one.mindly.pip.R
import one.mindly.pip.appGraph

/**
 * Home-screen widget: a "Catch" button, a "Hey Pip" on/off toggle, and "Open" for the app.
 *
 * Catch and the toggle go through QuickActionActivity (see there for why); Open just opens MainActivity. The toggle's label follows the
 * setting: PipApp calls [updateAll] whenever "Hey Pip" is switched on or off, from anywhere.
 */
class PipWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        manager.updateAppWidget(widgetIds, views(context))
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PipWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val listening = context.appGraph.settings.values.value.listeningEnabled
            return RemoteViews(context.packageName, R.layout.widget_pip).apply {
                setTextViewText(
                    R.id.widget_toggle,
                    context.getString(if (listening) R.string.widget_hey_pip_on else R.string.widget_hey_pip_off),
                )
                setOnClickPendingIntent(R.id.widget_catch, open(context, QuickActionActivity.ACTION_CATCH, 1))
                setOnClickPendingIntent(R.id.widget_toggle, open(context, QuickActionActivity.ACTION_TOGGLE_LISTENING, 2))
                setOnClickPendingIntent(
                    R.id.widget_open,
                    PendingIntent.getActivity(
                        context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        }

        private fun open(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode, QuickActionActivity.intent(context, action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
