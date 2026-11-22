package one.mindly.pip.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import one.mindly.pip.MainActivity
import one.mindly.pip.R
import one.mindly.pip.appGraph
import one.mindly.pip.core.capture.EngineStatus

/**
 * Home-screen widget, 2 x 2. Top row: "Pip ›" opens the app, and the "Hey Pip" switch turns
 * wake-word listening on and off. Everything under it is one big round button: it catches a thought,
 * and while Pip is catching it shows "Listening…" with a stop square and finishes and saves the note.
 *
 * Catch, finish and the switch go through QuickActionActivity (see there for why); "Pip ›" just
 * opens MainActivity. It follows Pip's own dark-mode setting (two layouts). PipApp calls
 * [updateAll] whenever Hey Pip, dark mode or the "catching" state changes, from anywhere.
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
            val graph = context.appGraph
            val settings = graph.settings.values.value
            return build(
                context,
                catching = graph.engineStatus.value == EngineStatus.Capturing,
                dark = settings.darkMode,
                heyPipOn = settings.listeningEnabled,
            )
        }

        /** The widget for a given state. Separate from [views] so a test can render every state. */
        internal fun build(context: Context, catching: Boolean, dark: Boolean, heyPipOn: Boolean): RemoteViews =
            RemoteViews(context.packageName, if (dark) R.layout.widget_pip_dark else R.layout.widget_pip).apply {
                setViewVisibility(R.id.widget_idle, if (catching) View.GONE else View.VISIBLE)
                setViewVisibility(R.id.widget_live, if (catching) View.VISIBLE else View.GONE)

                setImageViewResource(
                    R.id.widget_switch,
                    when {
                        heyPipOn && dark -> R.drawable.widget_sw_on_d
                        heyPipOn -> R.drawable.widget_sw_on_l
                        dark -> R.drawable.widget_sw_off_d
                        else -> R.drawable.widget_sw_off_l
                    },
                )
                setContentDescription(R.id.widget_hey, context.getString(if (heyPipOn) R.string.widget_desc_switch_on else R.string.widget_desc_switch_off))
                setContentDescription(R.id.widget_button, context.getString(if (catching) R.string.widget_desc_finish else R.string.widget_desc_catch))
                setContentDescription(R.id.widget_open, context.getString(R.string.widget_desc_open))

                setOnClickPendingIntent(
                    R.id.widget_button,
                    quick(context, if (catching) QuickActionActivity.ACTION_FINISH else QuickActionActivity.ACTION_CATCH, 1),
                )
                setOnClickPendingIntent(R.id.widget_hey, quick(context, QuickActionActivity.ACTION_TOGGLE_LISTENING, 2))
                setOnClickPendingIntent(
                    R.id.widget_open,
                    PendingIntent.getActivity(
                        context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }

        private fun quick(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode, QuickActionActivity.intent(context, action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
