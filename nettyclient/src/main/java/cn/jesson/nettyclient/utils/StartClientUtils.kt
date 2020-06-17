package cn.jesson.nettyclient.utils

import android.app.Application
import android.content.Context
import cn.jesson.nettyclient.channeladapter.LocalChannelAdapter
import cn.jesson.nettyclient.core.ClientCore
import cn.jesson.nettyclient.notification.ServiceNotificationUtils

class StartClientUtils private constructor() {

    companion object {
        const val TAG = "StartClientUtils"
        private var instance: StartClientUtils? = null

        @Synchronized
        fun getInstance(): StartClientUtils {
            synchronized(StartClientUtils::class.java) {
                if (instance == null) {
                    instance = StartClientUtils()
                }
            }
            return instance!!
        }
    }

    fun startClientWithSimpleThread(
        context: Context,
        iClientParameterCallBack: ClientCore.IClientParameterCallBack,
        iChannelChange: LocalChannelAdapter.IChannelChange,
        host: String,
        port: Int
    ): ClientCore {
        synchronized(StartClientUtils) {
            LogUtil.d(TAG, "startClientWithSimpleThread::host is: $host and port is: $port")
            val clientCore = ClientCore.getInstance(context, iClientParameterCallBack, iChannelChange)
            clientCore.startClintWithSimpleThread(host, port)
            return clientCore
        }
    }

    fun startClientWithServer(
        context: Application, iClientParameterCallBack: ClientCore.IClientParameterCallBack,
        iChannelChange: LocalChannelAdapter.IChannelChange,
        host: String,
        port: Int,
        simpleKeepLive: Boolean
    ): ClientCore {
        synchronized(StartClientUtils) {
            LogUtil.d(TAG, "startClientWithServer::host is: $host and port is: $port")
            ServiceNotificationUtils.instance?.initContext(context)
            ServiceNotificationUtils.instance?.apply {
                mKeepLive = simpleKeepLive
            }
            val clientCore = ClientCore.getInstance(context, iClientParameterCallBack, iChannelChange)
            clientCore.startClientWithServer(host, port) //start by user
            return clientCore
        }
    }
}