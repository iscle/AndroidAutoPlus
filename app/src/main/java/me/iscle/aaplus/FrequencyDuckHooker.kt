package me.iscle.aaplus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "FrequencyDuckHooker"

// How the duck sounds. A high-shelf filter leaves everything below [SHELF_FREQUENCY_HZ] untouched
// and attenuates everything above it by [SHELF_GAIN_DB], so the bass keeps driving while vocals and
// highs step back — the "club" duck instead of a flat volume drop.
private const val SHELF_FREQUENCY_HZ = 220.0
private const val SHELF_GAIN_DB = -16.0

// Seconds the effect takes to fade fully in or out, so engaging/releasing the duck is a smooth
// sweep rather than an audible click.
private const val RAMP_SECONDS = 0.15

/**
 * Turns Android Auto's ducking (music drops in volume while navigation is speaking) into a
 * frequency-selective duck that keeps the low end.
 *
 * Everything here rides on stable framework APIs — [AudioManager.registerAudioPlaybackCallback] to
 * learn when navigation guidance is playing, and [AudioRecord.read] on the remote-submix capture
 * Android Auto uses to stream music to the head unit — so it does not depend on Android Auto's
 * (obfuscated, per-release) internals and keeps working across versions.
 *
 * The captured music stream is stereo while the separate navigation-voice capture is mono, so we
 * only shape stereo submix captures; the spoken guidance itself is never filtered.
 */
fun hookFrequencyDuck(context: Context) {
    val duckState = DuckState()

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val callbackThread = HandlerThread("aaplus-duck-watcher").apply { start() }
    val callbackHandler = Handler(callbackThread.looper)

    val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            // The framework hands us only the currently-audible players, so a navigation-guidance
            // usage being present means navigation is speaking right now.
            val navigating = configs.any {
                it.audioAttributes?.usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            }
            if (duckState.engaged != navigating) {
                duckState.engaged = navigating
                Log.d(TAG, "onPlaybackConfigChanged: navigation guidance active = $navigating")
            }
        }
    }
    audioManager.registerAudioPlaybackCallback(playbackCallback, callbackHandler)

    XposedHelpers.findAndHookMethod(
        AudioRecord::class.java,
        "read",
        ByteArray::class.java,
        Int::class.java,
        Int::class.java,
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam?) {
                if (param == null) return
                val audioRecord = param.thisObject as AudioRecord
                val read = param.result as Int

                // Only the music Android Auto captures from other apps, and only in stereo so the
                // mono navigation-voice capture is left alone.
                if (audioRecord.audioSource != MediaRecorder.AudioSource.REMOTE_SUBMIX) return
                if (read <= 0 || audioRecord.channelCount < 2) return

                val filter = filterFor(audioRecord, duckState)
                val buffer = param.args[0] as ByteArray
                val offset = param.args[1] as Int
                val byteBuffer = ByteBuffer.wrap(buffer, offset, read).order(ByteOrder.nativeOrder())

                when (val audioFormat = audioRecord.audioFormat) {
                    AudioFormat.ENCODING_PCM_16BIT -> {
                        if (read % 2 != 0) return
                        filter.processShorts(byteBuffer.asShortBuffer(), read / 2)
                    }

                    AudioFormat.ENCODING_PCM_FLOAT -> {
                        if (read % 4 != 0) return
                        filter.processFloats(byteBuffer.asFloatBuffer(), read / 4)
                    }

                    else -> Log.d(TAG, "afterHookedMethod: unsupported audio format: $audioFormat")
                }
            }
        }
    )
}

/** Whether navigation is currently speaking. Shared between the watcher thread and the audio path. */
private class DuckState {
    @Volatile
    var engaged: Boolean = false
}

// One filter per capture, since each carries its own sample rate, channel count and filter memory.
private val filters = Collections.synchronizedMap(WeakHashMap<AudioRecord, ShelfDuckFilter>())

private fun filterFor(audioRecord: AudioRecord, duckState: DuckState): ShelfDuckFilter =
    filters.getOrPut(audioRecord) {
        ShelfDuckFilter(audioRecord.sampleRate, audioRecord.channelCount, duckState)
    }

/**
 * A per-channel biquad high-shelf whose wet/dry balance ramps toward "fully ducked" while
 * navigation is speaking and back to "untouched" afterwards. Interleaved samples in, in place.
 */
private class ShelfDuckFilter(
    sampleRate: Int,
    private val channels: Int,
    private val duckState: DuckState,
) {
    // Transfer-function coefficients (normalised so a0 == 1), shared across channels.
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double

    // Per-channel Direct Form I delay lines.
    private val x1 = DoubleArray(channels)
    private val x2 = DoubleArray(channels)
    private val y1 = DoubleArray(channels)
    private val y2 = DoubleArray(channels)

    // 0 = dry (bypassed), 1 = fully filtered. Stepped one increment per frame toward the target.
    private var mix = 0.0
    private val mixStep = 1.0 / (RAMP_SECONDS * sampleRate).coerceAtLeast(1.0)

    init {
        // RBJ audio-EQ-cookbook high-shelf.
        val a = Math.pow(10.0, SHELF_GAIN_DB / 40.0)
        val w0 = 2.0 * Math.PI * SHELF_FREQUENCY_HZ / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1) - (a - 1) * cosW0 + twoSqrtAAlpha
        b0 = a * ((a + 1) + (a - 1) * cosW0 + twoSqrtAAlpha) / a0
        b1 = -2 * a * ((a - 1) + (a + 1) * cosW0) / a0
        b2 = a * ((a + 1) + (a - 1) * cosW0 - twoSqrtAAlpha) / a0
        a1 = 2 * ((a - 1) - (a + 1) * cosW0) / a0
        a2 = ((a + 1) - (a - 1) * cosW0 - twoSqrtAAlpha) / a0
    }

    fun processShorts(buffer: java.nio.ShortBuffer, count: Int) {
        val frames = count / channels
        var i = 0
        for (frame in 0 until frames) {
            val target = if (duckState.engaged) 1.0 else 0.0
            mix += (target - mix).coerceIn(-mixStep, mixStep)
            for (ch in 0 until channels) {
                val dry = buffer.get(i).toDouble()
                val wet = step(ch, dry)
                val out = dry + (wet - dry) * mix
                buffer.put(i, out.toInt().coerceIn(-32768, 32767).toShort())
                i++
            }
        }
    }

    fun processFloats(buffer: java.nio.FloatBuffer, count: Int) {
        val frames = count / channels
        var i = 0
        for (frame in 0 until frames) {
            val target = if (duckState.engaged) 1.0 else 0.0
            mix += (target - mix).coerceIn(-mixStep, mixStep)
            for (ch in 0 until channels) {
                val dry = buffer.get(i).toDouble()
                val wet = step(ch, dry)
                buffer.put(i, (dry + (wet - dry) * mix).toFloat())
                i++
            }
        }
    }

    private fun step(ch: Int, x0: Double): Double {
        val y0 = b0 * x0 + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]
        x2[ch] = x1[ch]
        x1[ch] = x0
        y2[ch] = y1[ch]
        y1[ch] = y0
        return y0
    }
}
