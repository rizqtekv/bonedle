package app.bonedle.ui.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.bonedle.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** The game's sounds. */
enum class Sound { Snap, Error, RegionDone, Solved }

/**
 * Short UI sounds, synthesised once into static audio buffers: no audio files ship with the
 * app. Played through the game/sonification channel, so they follow the media volume and the
 * player's Sound setting (callers check it).
 */
@Singleton
class SoundEffects @Inject constructor(@ApplicationScope private val scope: CoroutineScope) {
    private val tracks = ConcurrentHashMap<Sound, AudioTrack>()

    /** Builds every buffer in the background so the first play has no delay. */
    fun warmUp() {
        scope.launch { Sound.entries.forEach { track(it) } }
    }

    fun play(sound: Sound) {
        scope.launch {
            val t = track(sound) ?: return@launch
            runCatching {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
                t.reloadStaticData()
                t.play()
            }
        }
    }

    private fun track(sound: Sound): AudioTrack? = tracks[sound] ?: runCatching {
        val pcm = Synth.render(sound)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Synth.RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        t.write(pcm, 0, pcm.size)
        tracks.putIfAbsent(sound, t) ?: t
    }.getOrNull()
}

/** Tiny additive synth for the effects above. */
internal object Synth {
    const val RATE = 44_100

    fun render(sound: Sound): ShortArray = when (sound) {
        // A woody tock: high partial over a short body, fast decay.
        Sound.Snap -> mix(0.07, tone(1_400.0, 0.0, 0.07, 0.22, 55.0), tone(520.0, 0.0, 0.06, 0.20, 70.0))
        // Two falling low notes.
        Sound.Error -> mix(0.2, tone(240.0, 0.0, 0.09, 0.25, 25.0), tone(180.0, 0.09, 0.11, 0.25, 22.0))
        // A soft two-note chime.
        Sound.RegionDone -> mix(0.3, tone(880.0, 0.0, 0.3, 0.16, 12.0), tone(1_320.0, 0.05, 0.25, 0.12, 14.0))
        // A rising major arpeggio.
        Sound.Solved -> mix(
            0.75,
            tone(523.25, 0.0, 0.45, 0.16, 6.0),
            tone(659.25, 0.09, 0.45, 0.16, 6.0),
            tone(783.99, 0.18, 0.45, 0.16, 6.0),
            tone(1_046.5, 0.27, 0.48, 0.18, 5.5),
        )
    }

    /** A sine at [hz] starting at [start] s for [length] s, peak [amp], exponential [decay] per s. */
    private fun tone(hz: Double, start: Double, length: Double, amp: Double, decay: Double) = Tone(hz, start, length, amp, decay)

    private class Tone(val hz: Double, val start: Double, val length: Double, val amp: Double, val decay: Double)

    private fun mix(seconds: Double, vararg tones: Tone): ShortArray {
        val n = (seconds * RATE).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            var v = 0.0
            for (tone in tones) {
                val local = t - tone.start
                if (local < 0 || local > tone.length) continue
                // 3 ms attack avoids clicks.
                val attack = minOf(1.0, local / 0.003)
                v += tone.amp * attack * exp(-tone.decay * local) * sin(2 * PI * tone.hz * local)
            }
            out[i] = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}

/** The app's [SoundEffects]; null in previews and screenshot tests. */
val LocalSoundEffects = androidx.compose.runtime.staticCompositionLocalOf<SoundEffects?> { null }
