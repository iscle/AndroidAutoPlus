package me.iscle.aaplus

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class WazeHooker(
    private val classLoader: ClassLoader,
) {
    private val preferences = XSharedPreferences(
        Constants.PACKAGE_NAME,
        Constants.WAZE_SETTINGS
    )

    init {
        /*
         * We set the configuration at three points in time since the config gets overwritten
         * after syncing with the local database and also after syncing with the server.
         */
        try {
            XposedHelpers.findAndHookMethod(
                "com.waze.NativeManager",
                classLoader,
                "onNativeLooperPrepared",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        if (param == null) return
                        XposedBridge.log("NativeManager.onNativeLooperPrepared() called")
                        setPlaySpeedCameraSoundBelowSpeedLimitConfig()
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("Could not hook NativeManager.onNativeLooperPrepared()")
            XposedBridge.log(e)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.waze.ConfigManager",
                classLoader,
                "onConfigSynced",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        if (param == null) return
                        XposedBridge.log("ConfigManager.onConfigSynced() called")
                        setPlaySpeedCameraSoundBelowSpeedLimitConfig()
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("Could not hook ConfigManager.onConfigSynced()")
            XposedBridge.log(e)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.waze.config.PreferencesConfigNativeManager",
                classLoader,
                "onPreferencesConfigSynced",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        if (param == null) return
                        XposedBridge.log("PreferencesConfigNativeManager.onPreferencesConfigSynced() called")
                        setPlaySpeedCameraSoundBelowSpeedLimitConfig()
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("Could not hook PreferencesConfigNativeManager.onPreferencesConfigSynced()")
            XposedBridge.log(e)
        }
    }

    fun setPlaySpeedCameraSoundBelowSpeedLimitConfig() {
        if (preferences.getBoolean(
                Constants.SETTING_WAZE_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT,
                Constants.SETTING_WAZE_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT_DEFAULT
        )) {
            try {
                val configValuesClass = XposedHelpers.findClass(
                    "com.waze.config.ConfigValues",
                    classLoader
                )
                val playSpeedCameraSoundBelowSpeedLimitField = XposedHelpers.findField(
                    configValuesClass,
                    "CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT"
                )
                val configManagerClass = XposedHelpers.findClass(
                    "com.waze.ConfigManager",
                    classLoader
                )
                val configManager = XposedHelpers.callStaticMethod(
                    configManagerClass,
                    "getInstance"
                )
                val setConfigValueBoolMethod = XposedHelpers.findMethodExact(
                    configManagerClass,
                    "setConfigValueBool",
                    playSpeedCameraSoundBelowSpeedLimitField.type,
                    Boolean::class.javaPrimitiveType
                )
                XposedBridge.log("Setting CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT to true")
                setConfigValueBoolMethod.invoke(
                    configManager,
                    playSpeedCameraSoundBelowSpeedLimitField.get(null),
                    true
                )
            } catch (e: Exception) {
                XposedBridge.log("Could not set CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT to true")
                XposedBridge.log(e)
            }
        }
    }
}