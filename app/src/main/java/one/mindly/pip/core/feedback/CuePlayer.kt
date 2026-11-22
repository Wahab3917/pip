package one.mindly.pip.core.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import one.mindly.pip.R

enum class Cue { LISTENING, CAUGHT, MISSED }

/**
 * Plays Pip's audio + haptic cues (res/raw/cue_*.wav: soft kalimba plucks, made by tools/generate_cues.py). Sounds are preloaded into a SoundPool so they start in a
 * few milliseconds (the spec asks for under 300 ms from wake word to cue).
 *
 * Rule from the spec: sounds use the NOTIFICATION volume; haptics are used only when the
 * phone is in silent or vibrate mode (i.e. they replace the sound you wouldn't hear).
 * Flip [HAPTICS_ALWAYS] to also buzz when the ringer is on.
 */
class CuePlayer(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator =
        context.getSystemService(VibratorManager::class.java).defaultVibrator

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION) // → notification volume slider
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = mapOf(
        Cue.LISTENING to soundPool.load(context, R.raw.cue_listening, 1),
        Cue.CAUGHT to soundPool.load(context, R.raw.cue_caught, 1),
        Cue.MISSED to soundPool.load(context, R.raw.cue_missed, 1),
    )

    fun play(cue: Cue) {
        val ringerOn = audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL
        if (ringerOn) {
            soundPool.play(soundIds.getValue(cue), 1f, 1f, 1, 0, 1f)
        }
        if (!ringerOn || HAPTICS_ALWAYS) {
            vibrator.vibrate(hapticFor(cue))
        }
    }

    fun release() = soundPool.release()

    /** Source: "Pip — Design Guidelines" → Sound and haptics. */
    private fun hapticFor(cue: Cue): VibrationEffect = when (cue) {
        // One short tap: "go ahead".
        Cue.LISTENING -> VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
        // Two soft taps: "got it". (timings: wait, buzz, wait, buzz; amplitudes 0–255)
        Cue.CAUGHT -> VibrationEffect.createWaveform(
            longArrayOf(0, 25, 90, 25), intArrayOf(0, SOFT, 0, SOFT), -1
        )
        // One dull tap: "didn't catch that".
        Cue.MISSED -> VibrationEffect.createOneShot(60, DULL)
    }

    private companion object {
        const val HAPTICS_ALWAYS = false
        const val SOFT = 90
        const val DULL = 50
    }
}
