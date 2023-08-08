package me.iscle.aaplus

import android.app.Application
import android.content.Context
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

private const val TAG = "Hooker"

class Hooker : IXposedHookLoadPackage {
    private lateinit var applicationContext: Context

    private fun onCreate() {
        Log.d(TAG, "onCreate() called")

        try {
            hookMusicStreamVolume(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: hookMusicStreamVolume failed", e)
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam == null) return
        if (lpparam.packageName != "com.google.android.projection.gearhead") return

        // Hook Application.onCreate()
        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    if (param == null) return
                    this@Hooker.applicationContext = (param.thisObject as Context).applicationContext
                    onCreate()
                }
            }
        )
    }
}