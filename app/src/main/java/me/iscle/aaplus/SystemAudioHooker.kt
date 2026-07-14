package me.iscle.aaplus

import android.media.AudioAttributes
import android.media.audiofx.Equalizer
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers

private const val TAG = "SystemAudioHooker"

// Android usage/state constants, inlined so we don't depend on hidden framework fields.
private const val USAGE_ASSISTANCE_NAVIGATION_GUIDANCE = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
private const val USAGE_MEDIA = AudioAttributes.USAGE_MEDIA
private const val USAGE_GAME = AudioAttributes.USAGE_GAME
private const val PLAYER_STATE_STARTED = 2

// Shape of the duck: keep everything below this untouched, pull the rest down by this much.
private const val SHELF_FREQUENCY_HZ = 250
private const val SHELF_CUT_MILLIBEL = -1400

/**
 * Runs inside `system_server`. Turns the OS's volume-based audio ducking into a bass-preserving one
 * for navigation: when a navigation app takes transient "may duck" focus, instead of letting the
 * framework fade the music down in volume we leave its level alone and hang a high-cut equalizer on
 * the music player's audio session, so the low end keeps driving while the mids/highs step back for
 * the spoken directions — the way a club or radio lowers a track under an announcement.
 *
 * This is where the duck is actually applied. Doing it from inside Android Auto is impossible: by
 * the time Android Auto captures the car audio the OS has already ducked the music (and mixed the
 * navigation voice into the same stream), so the volume drop can only be changed here.
 *
 * Everything is best-effort and falls back to the stock volume duck: if the app is not a navigation
 * app, the feature is off, the player has no session id (Android < 12), or attaching the effect
 * fails, the original method runs untouched. Audio is never left un-ducked by accident.
 */
class SystemAudioHooker(
    private val classLoader: ClassLoader
) {
    private val settings = XSharedPreferences(
        Constants.PACKAGE_NAME,
        Constants.ANDROID_AUTO_SETTINGS
    )

    // Active high-cut effects keyed by audio session id, so restore can tear them down.
    private val activeEffects = HashMap<Int, Equalizer>()

    init {
        val monitor = XposedHelpers.findClass(
            "com.android.server.audio.PlaybackActivityMonitor",
            classLoader
        )
        val focusRequester = XposedHelpers.findClass(
            "com.android.server.audio.FocusRequester",
            classLoader
        )

        // Replace the volume duck with a frequency duck for navigation.
        XposedHelpers.findAndHookMethod(
            monitor,
            "duckPlayers",
            focusRequester,
            focusRequester,
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        if (frequencyDuck(param.thisObject, param.args[0], param.args[1])) {
                            // We handled the duck via an effect; skip the framework's volume duck.
                            param.result = true
                        }
                    } catch (e: Throwable) {
                        // Never let our code break audio focus handling — fall through to stock.
                        Log.e(TAG, "duckPlayers hook failed; using stock ducking", e)
                        clearEffects()
                    }
                }
            }
        )

        // Undo our effects whenever the framework restores/unducks. Method was renamed across
        // versions, so hook whichever name exists; both take a single FocusRequester.
        for (name in arrayOf("restoreVShapedPlayers", "unduckPlayers")) {
            try {
                XposedHelpers.findAndHookMethod(
                    monitor,
                    name,
                    focusRequester,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            clearEffects()
                        }
                    }
                )
            } catch (e: NoSuchMethodError) {
                // Not present on this version; the other name covers it.
            }
        }
    }

    /**
     * Applies the high-cut effect to the loser's music players when [winner] is a navigation app.
     * Returns true only if at least one effect was attached, meaning the caller should skip the
     * framework's own volume duck.
     */
    private fun frequencyDuck(monitor: Any, winner: Any, loser: Any): Boolean {
        settings.reload()
        if (!settings.getBoolean(
                Constants.SETTING_ANDROID_AUTO_FREQUENCY_DUCK,
                Constants.SETTING_ANDROID_AUTO_FREQUENCY_DUCK_DEFAULT
        )) {
            return false
        }

        if (usageOf(winner) != USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) return false

        val loserUid = XposedHelpers.callMethod(loser, "getClientUid") as Int

        @Suppress("UNCHECKED_CAST")
        val players = (XposedHelpers.getObjectField(monitor, "mPlayers") as Map<*, *>)
            .values.toList()

        var attached = false
        for (apc in players) {
            if (apc == null) continue
            if (XposedHelpers.callMethod(apc, "getClientUid") as Int != loserUid) continue
            if (XposedHelpers.callMethod(apc, "getPlayerState") as Int != PLAYER_STATE_STARTED) continue

            val usage = (XposedHelpers.callMethod(apc, "getAudioAttributes") as AudioAttributes).usage
            if (usage != USAGE_MEDIA && usage != USAGE_GAME) continue

            // getSessionId() only exists from Android 12; on older versions this throws and we bail
            // out for the whole request, so the stock volume duck runs instead.
            val sessionId = XposedHelpers.callMethod(apc, "getSessionId") as Int
            if (sessionId <= 0 || activeEffects.containsKey(sessionId)) continue

            if (attachHighCut(sessionId)) attached = true
        }
        return attached
    }

    private fun usageOf(focusRequester: Any): Int =
        (XposedHelpers.callMethod(focusRequester, "getAudioAttributes") as AudioAttributes).usage

    private fun attachHighCut(sessionId: Int): Boolean {
        return try {
            val equalizer = Equalizer(0, sessionId)
            val bands = equalizer.numberOfBands.toInt()
            val minLevel = equalizer.bandLevelRange[0].toInt()
            val cut = maxOf(minLevel, SHELF_CUT_MILLIBEL).toShort()
            for (band in 0 until bands) {
                val hz = equalizer.getCenterFreq(band.toShort()) / 1000
                equalizer.setBandLevel(band.toShort(), if (hz >= SHELF_FREQUENCY_HZ) cut else 0)
            }
            equalizer.enabled = true
            synchronized(activeEffects) { activeEffects[sessionId] = equalizer }
            Log.d(TAG, "attachHighCut: engaged on session $sessionId ($bands bands)")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "attachHighCut: could not attach on session $sessionId", e)
            false
        }
    }

    private fun clearEffects() {
        synchronized(activeEffects) {
            for (equalizer in activeEffects.values) {
                try {
                    equalizer.enabled = false
                    equalizer.release()
                } catch (e: Throwable) {
                    Log.e(TAG, "clearEffects: release failed", e)
                }
            }
            activeEffects.clear()
        }
    }
}
