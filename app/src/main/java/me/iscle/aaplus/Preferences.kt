package me.iscle.aaplus

import android.content.Context
import android.content.SharedPreferences

/**
 * Returns a world-readable [SharedPreferences] so the Xposed hooks (running inside the target
 * apps) can read it via `XSharedPreferences`.
 *
 * `MODE_WORLD_READABLE` was removed from Android in API 24 and throws [SecurityException], but
 * LSPosed restores it for modules that declare the `xposedsharedprefs` meta-data (see
 * AndroidManifest). When the module process was not injected by LSPosed — e.g. the framework is
 * not installed/active, or the module is disabled — the call still throws; we fall back to a
 * private preferences file so the settings UI opens instead of crashing. (In that state the
 * hooks can't read the values anyway, since the module isn't running.)
 */
fun Context.getModuleSharedPreferences(name: String): SharedPreferences =
    try {
        getSharedPreferences(name, Context.MODE_WORLD_READABLE)
    } catch (e: SecurityException) {
        getSharedPreferences(name, Context.MODE_PRIVATE)
    }
