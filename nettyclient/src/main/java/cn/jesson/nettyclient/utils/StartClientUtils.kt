package cn.jesson.nettyclient.utils

import android.content.Context
import cn.jesson.nettyclient.core.ClientCore

class StartClientUtils private constructor() {


    private var mContext: Context? = null
    private var mIGetNettyClientParameter: ClientCore.IGetNettyClientParameter? = null
    private var mHost: String? = null
    private var mPort: Int? = null

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
        iGetNettyClientParameter: ClientCore.IGetNettyClientParameter,
        host: String,
        port: Int
    ): ClientCore {
        synchronized(StartClientUtils) {
            LogUtil.d(TAG, "startClientWithSimpleThread::host is: $host and port is: $port")
            val clientCore = ClientCore(context, iGetNettyClientParameter)
            clientCore.startClintWithSimpleThread(host, port)
            return clientCore
        }
    }

    fun startClientWithServer(
        context: Context, iGetNettyClientParameter: ClientCore.IGetNettyClientParameter,
        host: String,
        port: Int
    ): ClientCore {
        synchronized(StartClientUtils) {
            LogUtil.d(TAG, "startClientWithServer::host is: $host and port is: $port")
            val clientCore = ClientCore(context, iGetNettyClientParameter)
            clientCore.startClientWithServer(host, port) //start by user
            return clientCore
        }
    }
}