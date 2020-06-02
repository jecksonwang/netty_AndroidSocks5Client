package cn.jesson.nettyclient

import android.app.Application
import com.squareup.leakcanary.LeakCanary

class CustomApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if(LeakCanary.isInAnalyzerProcess(this)){
            return
        }
        LeakCanary.install(this)
    }
}