package me.iscle.aaplus

import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

private const val TAG = "Hooker"

class Hooker : IXposedHookLoadPackage {
    private lateinit var applicationContext: Context

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam == null) return

        when (lpparam.packageName) {
            "com.waze" -> WazeHooker(lpparam.classLoader)
            "com.google.android.projection.gearhead" -> AndroidAutoHooker(lpparam.classLoader)
        }
    }
}