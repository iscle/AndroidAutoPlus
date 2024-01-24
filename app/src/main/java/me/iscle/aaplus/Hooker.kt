package me.iscle.aaplus

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class Hooker : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam == null) return
        when (lpparam.packageName) {
            "com.google.android.projection.gearhead" -> AndroidAutoHooker(lpparam.classLoader)
            "com.waze" -> WazeHooker(lpparam.classLoader)
        }
    }
}