package ir.mahdiparastesh.instatools.more

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.more.Intelligence

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FLAVOR == "galaxy")
            registerActivityLifecycleCallbacks(AppLifecycleTracker())
    }

    inner class AppLifecycleTracker : ActivityLifecycleCallbacks {
        private var stack = 0

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}

        override fun onActivityStarted(activity: Activity) {
            stack++
        }

        override fun onActivityStopped(activity: Activity) {
            stack--
            if (stack == 0 && Intelligence.unCensorMain)
                packageManager.setComponentEnabledSetting(
                    ComponentName(
                        packageName, "${activity.javaClass.`package`!!.name}.Main\$TmCensored"
                    ), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
                )
        }
    }
}
