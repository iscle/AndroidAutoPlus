package me.iscle.aaplus

import android.app.Application
import android.content.Context
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

private const val TAG = "AndroidAutoHooker"

class AndroidAutoHooker(
    private val classLoader: ClassLoader
) {

    init {
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    if (param == null) return
                    val applicationContext = (param.thisObject as Context).applicationContext

                    try {
                        hookMusicStreamVolume(applicationContext)
                    } catch (e: Exception) {
                        Log.e(TAG, "onCreate: hookMusicStreamVolume failed", e)
                    }
                }
            }
        )
    }
}