package cn.jesson.nettyclient.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import cn.jesson.nettyclient.channeladapter.LocalChannelAdapter
import cn.jesson.nettyclient.decode.LocalByteToMessageDecoder
import cn.jesson.nettyclient.server.ForegroundServer
import cn.jesson.nettyclient.utils.Error
import cn.jesson.nettyclient.utils.LogUtil
import cn.jesson.nettyclient.utils.NetworkUtils
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.timeout.IdleStateHandler
import java.util.concurrent.TimeUnit

class ClientCore(ctx: Context, iGetNettyClientParameter: IGetNettyClientParameter) :
    LocalChannelAdapter.INotifyClientCoreConnectState, ForegroundServer.IClientAction {

    companion object {
        const val TAG = "ClientCore"
    }

    private var mContext: Context? = ctx
    private var mChannel: Channel? = null
    private var mHandler: Handler? = null
    private var mIGetNettyClientParameter: IGetNettyClientParameter? = iGetNettyClientParameter
    private var mHost: String? = null
    private var mPort: Int? = null

    private var mServiceConnection: CustomServiceConnection? = null
    private var mBindServer: Boolean = false

    private var mStartType: Int = 0

    var mReadPingTimeOut: Long = 20000
    var mWritePingTimeOut: Long = 20000
    var mAllPingTimeOut: Long = 0

    var byteToMessageDecoder: LocalByteToMessageDecoder? = null
    var localChannelAdapter: LocalChannelAdapter? = null
    var nioEventLoopGroup: NioEventLoopGroup? = null

    var mAutoReconnect: Boolean = true
    var mAutoReconnectFrequency: Int = 5
    var mAutoReconnectIntervalTime: Long = 2000
    private var mReconnectNum = 0 //Current number of reconnection
    private var stopAutoReconnect: Boolean = false

    init {
        mHandler = Handler(Looper.getMainLooper())
    }

    fun startClintWithSimpleThread(host: String, port: Int) {
        LogUtil.d(TAG, "startClintWithSimpleThread")
        mStartType = 1
        val thread = Thread(Runnable {
            LogUtil.d(TAG, "startClintWithSimpleThread::thread run")
            connect(host, port)
            LogUtil.d(TAG, "startClintWithSimpleThread::thread release")
        })
        thread.start()
    }

    fun startClientWithServer(host: String, port: Int) {
        LogUtil.d(TAG, "startClientWithServer::mBindServer is: $mBindServer")
        ForegroundServer.setActionListener(this)
        mStartType = 2
        if (!mBindServer) {
            LogUtil.d(TAG, "startClientWithServer::do bind server")
            this.mHost = host
            this.mPort = port
            bindClientServer(mContext)
        } else {
            startClientWithServerInternal(mContext, host, port)
        }
    }

    fun connect(host: String, port: Int) {
        LogUtil.d(TAG, "connect::host is: $host and port is: $port")
        if (!NetworkUtils.isConnected(mContext)) {
            return
        }
        if (nioEventLoopGroup == null) {
            nioEventLoopGroup = NioEventLoopGroup()
        }
        try {
            var f: ChannelFuture? = null
            val bootstrap = Bootstrap()
            bootstrap.group(nioEventLoopGroup)
            bootstrap.channel(NioSocketChannel::class.java)
            bootstrap.option(ChannelOption.TCP_NODELAY, true)
            bootstrap.option(ChannelOption.SO_KEEPALIVE, true)
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            bootstrap.handler(object : ChannelInitializer<SocketChannel>() {
                @Throws(Exception::class)
                override fun initChannel(ch: SocketChannel) {
                    byteToMessageDecoder = mIGetNettyClientParameter?.getMessageDecoder()
                    localChannelAdapter = mIGetNettyClientParameter?.getChannelAdapter()
                    if (byteToMessageDecoder == null || localChannelAdapter == null) {
                        throw IllegalArgumentException("connect->message decoder is null or channel adapter is null, please check")
                    }
                    localChannelAdapter?.apply {
                        mINotifyProxyStateChange = byteToMessageDecoder
                        mINotifyClientCoreConnectState = this@ClientCore
                    }
                    ch.pipeline().addLast("localDecoder", byteToMessageDecoder)
                    ch.pipeline().addLast(
                        "localIdleStateHandler",
                        IdleStateHandler(
                            mReadPingTimeOut,
                            mWritePingTimeOut,
                            mAllPingTimeOut,
                            TimeUnit.MILLISECONDS
                        )
                    )
                    ch.pipeline()
                        .addLast("localChannelAdapter", localChannelAdapter)
                }
            })
            f = bootstrap.connect(host, port).awaitUninterruptibly()
            if (f.isDone) {
                LogUtil.d(TAG, "connect::connect done")
                if (f.isSuccess) {
                    LogUtil.d(TAG, "connect::connect success")
                    f.channel().closeFuture().sync()
                } else {
                    LogUtil.d(TAG, "connect::connect fail")
                    if (f.channel() != null) {
                        f.channel().close()
                    }
                }
            } else {
                LogUtil.d(TAG, "connect::connect not done")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            LogUtil.d(TAG, "do finally when channel close")
            localChannelAdapter?.mIChannelChange?.channelStateChange(
                localChannelAdapter?.mSimpleProxy,
                connectProxyState = false,
                connectTargetState = false,
                errorCode = Error.CONNECT_RELEASE
            )
            closeConnectInternal(host, port)
        }
    }

    fun closeConnect() {
        LogUtil.d(TAG, "closeConnect")
        mReconnectNum = 0
        stopAutoReconnect = true
        doCloseConnect()
    }

    private fun closeConnectInternal(host: String, port: Int) {
        if (!stopAutoReconnect) {
            doCloseConnect()
            doReconnect(host, port)
        } else {
            stopAutoReconnect = false //reset
        }
    }

    private fun doCloseConnect() {
        LogUtil.d(TAG, "doCloseConnect")
        ForegroundServer.removeActionListener()
        localChannelAdapter?.mINotifyClientCoreConnectState = null
        localChannelAdapter?.mINotifyProxyStateChange = null
        nioEventLoopGroup?.shutdownGracefully()
        nioEventLoopGroup = null
        mChannel?.close()
        mChannel = null
    }

    private fun doReconnect(host: String, port: Int) {
        if (mAutoReconnect && NetworkUtils.isConnected(mContext)) {
            if (mReconnectNum == mAutoReconnectFrequency) {
                //if current retry time == max frequency, stop reconnect
                LogUtil.d(TAG, "doReconnect::stop retry")
                mReconnectNum = 0
                return
            } else {
                mHandler?.postDelayed({
                    if (!stopAutoReconnect) {
                        mReconnectNum++
                        LogUtil.d(TAG, "doReconnect::current retry num is: $mReconnectNum")
                        when(mStartType){
                            1->{
                                startClintWithSimpleThread(host, port)
                            }
                            2->{
                                startClientWithServer(host, port) //restart internal
                            }
                        }
                    } else {
                        mReconnectNum = 0
                        LogUtil.d(TAG, "doReconnect::stop reconnect by stop")
                    }
                }, mAutoReconnectIntervalTime)
            }
        }
    }

    fun checkConnectState(tag: String): Boolean {
        if (mChannel != null && mChannel!!.isOpen && mChannel!!.isActive) {
            LogUtil.d(TAG, "checkConnectState::connect ok check tag is: $tag")
            return true
        }
        LogUtil.d(TAG, "checkConnectState::connect bad check tag is: $tag")
        return false
    }


    private fun startClientWithServerInternal(
        context: Context?,
        host: String?,
        port: Int?
    ) {
        if (context == null || host == null || port == null) {
            LogUtil.e(TAG, "startClientWithServerInternal::start error please check param")
            return
        }
        ForegroundServer.startClientWithServer(context, host, port)
    }

    private fun bindClientServer(context: Context?) {
        if (context == null) {
            LogUtil.e(TAG, "bindClientServer::context is null")
            return
        }
        if (mServiceConnection == null) {
            mServiceConnection = CustomServiceConnection()
        }
        val intent = Intent(context, ForegroundServer::class.java)
        context.bindService(intent, mServiceConnection!!, Context.BIND_AUTO_CREATE)
    }

    inner class CustomServiceConnection : ServiceConnection {

        override fun onServiceDisconnected(name: ComponentName?) {
            LogUtil.d(TAG, "onServiceDisconnected")
            mBindServer = false
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            LogUtil.d(TAG, "onServiceConnected")
            mBindServer = true
            val client: ForegroundServer.GetNettyClient = service as ForegroundServer.GetNettyClient
            val server = client.getServer()
            server.startForegroundNotify()
            startClientWithServerInternal(mContext, mHost, mPort)
        }
    }


    override fun notifyClientCoreConnectSuccess(channel: Channel?) {
        LogUtil.d(TAG, "notifyClientCoreConnectSuccess::channel state is: ${channel?.isOpen}")
        mReconnectNum = 0 //when connect target success, reset it
        mChannel = channel
    }

    override fun notifyClientCoreProxyAuthError() {
        LogUtil.d(TAG, "notifyClientCoreProxyAuthError::stop auto reconnect")
        mReconnectNum = 0
        stopAutoReconnect =
            true //if proxy auth error by proxy server, stop reconnect, and notify user by IChannelChange.channelStateChange
    }

    override fun actionConnect(host: String, port: Int) {
        LogUtil.d(TAG, "actionConnect::host is: $host and port is: $port")
        connect(host, port)
    }

    override fun actionCheckConnect(): Boolean {
        return checkConnectState("actionCheckConnect")
    }

    interface IGetNettyClientParameter {
        fun getMessageDecoder(): LocalByteToMessageDecoder
        fun getChannelAdapter(): LocalChannelAdapter
    }

}