package me.iscle.aaplus

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method

class WazeHooker(
    private val classLoader: ClassLoader,
) {
    private val preferences = XSharedPreferences(
        Constants.PACKAGE_NAME,
        Constants.WAZE_SETTINGS
    )

    // Resolved lazily on first use and cached: neither the config field nor the setter
    // change during the lifetime of the process, and the re-apply hooks below can fire
    // several times per config sync, so we avoid repeating the reflection lookups.
    private var configValueField: Field? = null
    private var setConfigValueBoolMethod: Method? = null
    private var resolveFailed = false

    private val reapplyHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            setPlaySpeedCameraSoundBelowSpeedLimitConfig()
        }
    }

    init {
        /*
         * The config value we set gets overwritten every time Waze syncs its configuration
         * (once when the native layer comes up, and again after each sync with the local
         * database / server), so we re-apply it after every such event.
         *
         * We deliberately anchor on the *non-obfuscated* manager classes and reach the
         * callbacks through the class hierarchy. The relevant callbacks are JNI entry points
         * invoked from native code by name, so their names are part of the native binding
         * contract and stay stable across updates. We also match the config-sync callbacks by
         * name prefix instead of an exact name: the single `onConfigSynced()` callback was
         * split into `onConfigSyncedFromServer()` / `onConfigSyncedToServer()` in a recent
         * update (which is what broke this hook), and prefix matching absorbs that kind of
         * refactor automatically while also covering the `...JNI` native entry points.
         */

        // Initial application, as soon as the native looper (and thus the config layer) is up.
        hookMatchingMethods(
            className = "com.waze.NativeManager",
            description = "NativeManager#onNativeLooperPrepared",
        ) { it.name == "onNativeLooperPrepared" && it.parameterTypes.isEmpty() }

        // Re-apply after every configuration sync.
        hookMatchingMethods(
            className = "com.waze.ConfigManager",
            description = "ConfigManager#onConfigSynced*",
        ) { it.name.startsWith("onConfigSynced") && it.parameterTypes.isEmpty() }

        // Re-apply after the preferences configuration sync.
        hookMatchingMethods(
            className = "com.waze.config.PreferencesConfigNativeManager",
            description = "PreferencesConfigNativeManager#onPreferencesConfigSynced",
        ) { it.name == "onPreferencesConfigSynced" && it.parameterTypes.isEmpty() }
    }

    /**
     * Hooks every no-arg method (declared on [className] or any of its superclasses) that
     * matches [predicate], attaching [reapplyHook]. Each match is hooked independently so a
     * single failure cannot take the others down, and a warning is logged if nothing matched
     * (which is the signal that Waze changed something and the hook needs revisiting).
     */
    private fun hookMatchingMethods(
        className: String,
        description: String,
        predicate: (Method) -> Boolean,
    ) {
        try {
            var clazz: Class<*>? = XposedHelpers.findClass(className, classLoader)
            var hooked = 0
            while (clazz != null && clazz != Any::class.java) {
                for (method in clazz.declaredMethods) {
                    if (!predicate(method)) continue
                    try {
                        XposedBridge.hookMethod(method, reapplyHook)
                        hooked++
                        XposedBridge.log("WazeHooker: hooked ${clazz.name}#${method.name}() for $description")
                    } catch (e: Throwable) {
                        XposedBridge.log("WazeHooker: failed to hook ${clazz.name}#${method.name}() for $description")
                        XposedBridge.log(e)
                    }
                }
                clazz = clazz.superclass
            }
            if (hooked == 0) {
                XposedBridge.log("WazeHooker: WARNING - no methods matched for $description; Waze may have changed, the hook needs updating")
            }
        } catch (e: Throwable) {
            XposedBridge.log("WazeHooker: could not install hook for $description")
            XposedBridge.log(e)
        }
    }

    fun setPlaySpeedCameraSoundBelowSpeedLimitConfig() {
        if (!preferences.getBoolean(
                Constants.SETTING_WAZE_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT,
                Constants.SETTING_WAZE_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT_DEFAULT
            )
        ) {
            return
        }

        val field = resolveConfigValueField() ?: return
        val setter = resolveSetConfigValueBoolMethod(field) ?: return

        try {
            val configManagerClass = XposedHelpers.findClass("com.waze.ConfigManager", classLoader)
            val configManager = XposedHelpers.callStaticMethod(configManagerClass, "getInstance")
            XposedBridge.log("WazeHooker: setting CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT to true")
            setter.invoke(configManager, field.get(null), true)
        } catch (e: Throwable) {
            XposedBridge.log("WazeHooker: could not set CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT to true")
            XposedBridge.log(e)
        }
    }

    private fun resolveConfigValueField(): Field? {
        configValueField?.let { return it }
        if (resolveFailed) return null
        return try {
            val configValuesClass = XposedHelpers.findClass("com.waze.config.ConfigValues", classLoader)
            XposedHelpers.findField(
                configValuesClass,
                "CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT"
            ).also { configValueField = it }
        } catch (e: Throwable) {
            resolveFailed = true
            XposedBridge.log("WazeHooker: could not resolve CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT field")
            XposedBridge.log(e)
            null
        }
    }

    private fun resolveSetConfigValueBoolMethod(field: Field): Method? {
        setConfigValueBoolMethod?.let { return it }
        if (resolveFailed) return null
        return try {
            val configManagerClass = XposedHelpers.findClass("com.waze.ConfigManager", classLoader)
            // The setter takes the config descriptor (the field's own type, e.g. com.waze.config.b)
            // and the boolean value. Resolving against field.type keeps us correct even if Waze
            // renames that descriptor class in a future update.
            XposedHelpers.findMethodExact(
                configManagerClass,
                "setConfigValueBool",
                field.type,
                Boolean::class.javaPrimitiveType
            ).also { setConfigValueBoolMethod = it }
        } catch (e: Throwable) {
            resolveFailed = true
            XposedBridge.log("WazeHooker: could not resolve ConfigManager#setConfigValueBool")
            XposedBridge.log(e)
            null
        }
    }
}
