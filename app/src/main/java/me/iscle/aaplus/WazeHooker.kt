package me.iscle.aaplus

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.Thread.sleep
import java.lang.reflect.Proxy

private const val TAG = "WazeHooker"

class WazeHooker(
    private val classLoader: ClassLoader,
) {
    
    init {
        try {
            val configValuesClass = XposedHelpers.findClass(
                "com.waze.config.ConfigValues",
                classLoader
            )

            val playSpeedCameraSoundBelowSpeedLimitField = XposedHelpers.findField(
                configValuesClass,
                "CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT"
            )

            XposedBridge.hookMethod(
                playSpeedCameraSoundBelowSpeedLimitField.type.declaredConstructors[0],
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        if (param == null) return
                        val name = param.args[3] as String
                        if (name == "CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT") {
                            Log.d(TAG, "beforeHookedMethod: Replacing CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT defaultValue with proxy")
                            param.args[4] = Proxy.newProxyInstance(
                                classLoader,
                                arrayOf(param.args[4].javaClass)
                            ) { proxy, method, args ->
                                if (method?.name != "get") throw IllegalStateException("Unexpected method call")
                                Log.d(TAG, "beforeHookedMethod: CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT.get() called, returning true")
                                true
                            }
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                "com.waze.NativeManager",
                classLoader,
                "onNativeLooperPrepared",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        if (param == null) return
                        Log.d(TAG, "afterHookedMethod: NativeManager.onNativeLooperPrepared() called")

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
                        Thread {
                            var count = 0;
                            while (count < 3) {
                                try {
                                    Log.d(TAG, "afterHookedMethod: Setting CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT to true")
                                    setConfigValueBoolMethod.invoke(
                                        configManager,
                                        playSpeedCameraSoundBelowSpeedLimitField.get(null),
                                        true
                                    )
                                    count++
                                } catch (e: Exception) {
                                    Log.e(TAG, "afterHookedMethod: Could not set CONFIG_VALUE_ALERTS_PLAY_SPEED_CAMERA_SOUND_BELOW_SPEED_LIMIT to true", e)
                                }
                                sleep(1500)
                            }
                        }.start()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "handleLoadPackage: Could not hook NativeManager.onNativeLooperPrepared()", e)
        }
    }
}