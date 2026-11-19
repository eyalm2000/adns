package com.eyalm.adns

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.eyalm.adns.data.Locales
import com.eyalm.adns.data.network.ApiClient
import com.eyalm.adns.data.runtime.RuntimeMonitoringRepositories
import com.eyalm.adns.data.security.ProtectionLocks
import com.eyalm.adns.data.wifi.WifiRulesRepositories

class AdnsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        Locales.init(this)
        RuntimeMonitoringRepositories.getInstance(this)
        WifiRulesRepositories.getInstance(this)
        registerProtectionLock()
    }

    // Counts the visible ADNS screens, so the lock knows when the app really left the foreground.
    private fun registerProtectionLock() {
        val lock = ProtectionLocks.getInstance(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = lock.onActivityStarted()
            override fun onActivityStopped(activity: Activity) = lock.onActivityStopped()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })    }
}
