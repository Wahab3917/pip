package one.mindly.pip.quick

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The "Catch" button in the quick-settings panel (swipe down from the top of the screen).
 * Tapping it records one thought, also from the lock screen, whether or not "Hey Pip" is on.
 *
 * The tile is a plain button, not an on/off switch, so it always shows as inactive.
 */
class CatchTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // The Intent version is the only one before Android 14.
    override fun onClick() {
        val intent = QuickActionActivity.intent(this, QuickActionActivity.ACTION_CATCH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
