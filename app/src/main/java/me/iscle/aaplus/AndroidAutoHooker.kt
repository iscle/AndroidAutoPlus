package me.iscle.aaplus

import android.app.Application
import android.content.Context
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers

private const val TAG = "AndroidAutoHooker"

class AndroidAutoHooker(
    private val classLoader: ClassLoader
) {
    private val settings = XSharedPreferences(
        Constants.PACKAGE_NAME,
        Constants.ANDROID_AUTO_SETTINGS
    )

    init {
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val applicationContext = (param.thisObject as Context).applicationContext

                    if (settings.getBoolean(
                            Constants.SETTING_ANDROID_AUTO_HOOK_MUSIC_STREAM_VOLUME,
                            Constants.SETTING_ANDROID_AUTO_HOOK_MUSIC_STREAM_VOLUME_DEFAULT
                    )) {
                        try {
                            Log.d(TAG, "onCreate: hookMusicStreamVolume")
                            hookMusicStreamVolume(applicationContext)
                        } catch (e: Exception) {
                            Log.e(TAG, "onCreate: hookMusicStreamVolume failed", e)
                        }
                    }
                }
            }
        )
    }
}