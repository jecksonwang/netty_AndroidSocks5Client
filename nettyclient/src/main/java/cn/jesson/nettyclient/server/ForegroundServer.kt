package cn.jesson.nettyclient.server

import android.app.IntentService
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat.startForegroundService
import cn.jesson.nettyclient.notification.ServiceNotificationUtils
import cn.jesson.nettyclient.utils.Constants
import cn.jesson.nettyclient.utils.NettyLogUtil

class ForegroundServer : IntentService("nettyForegroundServer") {


    companion object {

        const val TAG = "ForegroundServer"

        private var mClientAction: IClientAction? = null

        internal fun setActionListener(action: IClientAction) {
            mClientAction = action
        }

        internal fun removeActionListener() {
            mClientAction = null
        }

        internal fun startClientWithServer(context: Context, host: String, port: Int) {
            if(mClientAction == null){
                NettyLogUtil.e(TAG, "startClientWithServer::start server fail by client action is null")
                return
            }
            val actionCheckConnect = mClientAction!!.actionCheckConnect()
            if (!actionCheckConnect) {
                val intent = Intent(context, ForegroundServer::class.java)
                intent.putExtra("host", host)
                intent.putExtra("port", port)
                intent.action = "action_start_netty_client"
                startForegroundService(context, intent)
            } else {
                NettyLogUtil.d(TAG, "startClientWithServer::start server fail by server is ok")
            }
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
        val mKeepLive = ServiceNotificationUtils.instance?.mKeepLive
        NettyLogUtil.d(TAG, "onHandleIntent::in mKeepLive is: $mKeepLive")
        if(mKeepLive != null && !mKeepLive){
            removeNotify()
        }
        if (mClientAction == null) {
            NettyLogUtil.e(TAG, "onHandleIntent::start server fail by client action is null")
            return
        }
        val actionCheckConnect = mClientAction!!.actionCheckConnect()
        if (actionCheckConnect) {
            NettyLogUtil.d(TAG, "onHandleIntent::server is ok no need start")
            return
        }
        val host = intent?.getStringExtra("host")
        val port = intent?.getIntExtra("port", 0)
        if (host.isNullOrEmpty() || port == null) {
            NettyLogUtil.e(TAG, "onHandleIntent::start server fail by host or port invalid")
            return
        }
        NettyLogUtil.d(TAG, "onHandleIntent::host is: $host and port is: $port")
        mClientAction!!.actionConnect(host, port)
        NettyLogUtil.d(TAG, "onHandleIntent::release")
    }

    inner class GetNettyClient : Binder() {
        fun getServer(): ForegroundServer {
            return this@ForegroundServer
        }
    }

    internal fun startForegroundNotify() {
        NettyLogUtil.d(TAG, "startForegroundNotify")
        val build: Notification = ServiceNotificationUtils.instance?.getNotification()!!
        ServiceNotificationUtils.instance?.sendNotification(build)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForeground(Constants.FOREGROUND_SERVER_NOTIFY_ID, build)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun removeNotify(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            stopForeground(true)
        }
    }

    internal interface IClientAction {
        fun actionConnect(host: String, port: Int)
        fun actionCheckConnect(): Boolean
    }

}