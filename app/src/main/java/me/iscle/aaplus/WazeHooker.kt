package me.iscle.aaplus

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

private const val TAG = "WazeHooker"

class WazeHooker(
    private val classLoader: ClassLoader,
) {
    
    init {
        try {
            XposedHelpers.findAndHookMethod(
                "com.waze.NativeManager",
                classLoader,
                "onNativeLooperPrepared",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        if (param == null) return
                        Log.d(TAG, "afterHookedMethod: NativeManager.onNativeLooperPrepared() called")
                        setPlaySpeedCameraSoundBelowSpeedLimitConfig()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "handleLoadPackage: Could not hook NativeManager.onNativeLooperPrepared()", e)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.waze.ConfigManager",
                classLoader,
                "onConfigSynced",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        if (param == null) return
                        Log.d(TAG, "afterHookedMethod: ConfigManager.onConfigSynced() called")
                        setPlaySpeedCameraSoundBelowSpeedLimitConfig()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "handleLoadPackage: Could not hook ConfigManager.onConfigSynced()", e)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.waze.config.PreferencesConfigNativeManager",
                classLoader,
                "onPreferencesConfigSynced",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        if (param == null) return
                        Log.d(TAG, "afterHookedMethod: PreferencesConfigNativeManager.onPreferencesConfigSynced() called")
                        setPlaySpeedCameraSoundBelowSpeedLimitConfig()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "handleLoadPackage: Could not hook PreferencesConfigNativeManager.onPreferencesConfigSynced()", e)
        }
    }

    fun setPlaySpeedCameraSoundBelowSpeedLimitConfig() {
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
            Log.d(TAG, "setPlaySpeedCameraSoundBelowSpeedLimitConfig: Setting CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT to true")
            setConfigValueBoolMethod.invoke(
                configManager,
                playSpeedCameraSoundBelowSpeedLimitField.get(null),
                true
            )
        } catch (e: Exception) {
            Log.e(TAG, "setPlaySpeedCameraSoundBelowSpeedLimitConfig: Failed to set CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT", e)
        }
    }
}