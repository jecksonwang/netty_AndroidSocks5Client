package cn.jesson.nettyclient.server

import android.app.IntentService
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat.startForegroundService
import cn.jesson.nettyclient.core.ClientCore
import cn.jesson.nettyclient.notification.ServiceNotificationUtils
import cn.jesson.nettyclient.utils.Constants
import cn.jesson.nettyclient.utils.LogUtil

class ForegroundServer : IntentService("nettyForegroundServer") {


    companion object {

        const val TAG = "ForegroundServer"

        private var mClientAction: IClientAction? = null

        fun setActionListener(action: IClientAction){
            mClientAction = action
        }

        fun removeActionListener(){
            mClientAction = null
        }

        fun startClientWithServer(
            context: Context,
            host: String,
            port: Int
        ) {
            val intent = Intent(context, ForegroundServer::class.java)
            intent.putExtra("host", host)
            intent.putExtra("port", port)
            intent.action = "action_start_netty_client"
            startForegroundService(context, intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotify()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return GetNettyClient()
    }

    override fun onHandleIntent(intent: Intent?) {
        LogUtil.d(TAG, "onHandleIntent::in")
        if (mClientAction == null) {
            LogUtil.e(TAG, "onHandleIntent::start server fail by client action is null")
            return
        }
        val actionCheckConnect = mClientAction!!.actionCheckConnect()
        if(actionCheckConnect){
            LogUtil.d(TAG, "onHandleIntent::server is ok no need start")
            return
        }
        val host = intent?.getStringExtra("host")
        val port = intent?.getIntExtra("port", 0)
        if (host.isNullOrEmpty() || port == null) {
            LogUtil.e(TAG, "onHandleIntent::start server fail by host or port invalid")
            return
        }
        LogUtil.d(TAG, "onHandleIntent::host is: $host and port is: $port")
        mClientAction!!.actionConnect(host, port)
        LogUtil.d(TAG, "onHandleIntent::release")
    }

    inner class GetNettyClient : Binder() {
        fun getServer(): ForegroundServer {
            return this@ForegroundServer
        }
    }

    fun startForegroundNotify() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                LogUtil.d(TAG, "startForegroundNotify")
                ServiceNotificationUtils.instance?.setContext(this)
                val build: Notification = ServiceNotificationUtils.instance?.getChannelNotification(
                    "foreground server",
                    "we are running"
                )!!.build()
                ServiceNotificationUtils.instance?.sendNotification(build)
                startForeground(Constants.FOREGROUND_SERVER_NOTIFY_ID, build)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    interface IClientAction {
        fun actionConnect(host: String, port: Int)
        fun actionCheckConnect(): Boolean
    }

}