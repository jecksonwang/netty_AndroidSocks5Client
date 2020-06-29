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
import cn.jesson.nettyclient.utils.ConnectState
import cn.jesson.nettyclient.utils.NettyLogUtil
import cn.jesson.nettyclient.utils.NetworkUtils
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.timeout.IdleStateHandler
import java.util.concurrent.TimeUnit

class ClientCore private constructor(
    private var mContext: Context?,
    private var mIClientParameterCallBack: IClientParameterCallBack?,
    private var mIChannelChange: LocalChannelAdapter.IChannelChange?
) :
    LocalChannelAdapter.INotifyClientCoreConnectState, ForegroundServer.IClientAction {

    companion object {
        const val TAG = "ClientCore"
        private var instance: ClientCore? = null

        @Synchronized
        fun getInstance(
            ctx: Context,
            iClientParameterCallBack: IClientParameterCallBack,
            iChannelChange: LocalChannelAdapter.IChannelChange
        ): ClientCore {
            if (instance == null) {
                synchronized(ClientCore) {
                    NettyLogUtil.d(TAG, "getInstance::create new instance")
                    instance = ClientCore(ctx, iClientParameterCallBack, iChannelChange)
                }
            } else {
                this.instance!!.mContext = ctx
                this.instance!!.mIClientParameterCallBack = iClientParameterCallBack
                this.instance!!.mIChannelChange = iChannelChange
            }
            return instance!!
        }
    }

    var mReadPingTimeOut: Long = 20000
    var mWritePingTimeOut: Long = 20000
    var mAllPingTimeOut: Long = 0

    var mChannel: Channel? = null
    private var mHandler: Handler? = null
    private var mHost: String? = null
    private var mPort: Int? = null

    private var mServiceConnection: CustomServiceConnection? = null
    private var mBindServer: Boolean = false
    private var mStartType: Int = 0 //1 for simple thread, 2 for service

    private var byteToMessageDecoder: LocalByteToMessageDecoder? = null
    private var localChannelAdapter: LocalChannelAdapter? = null
    private var nioEventLoopGroup: NioEventLoopGroup? = null

    private var mAutoReconnect: Boolean = true
    private var mAutoReconnectFrequency: Int = 5
    private var mAutoReconnectIntervalTime: Long = 2000
    private var mReconnectNum = 0 //Current number of reconnection
    private var stopAutoReconnect: Boolean = false


    init {
        mHandler = Handler(Looper.getMainLooper())
    }

    internal fun startClintWithSimpleThread(host: String, port: Int) {
        NettyLogUtil.d(TAG, "startClintWithSimpleThread")
        mStartType = 1
        val thread = Thread(Runnable {
            NettyLogUtil.d(TAG, "startClintWithSimpleThread::thread run")
            connect(host, port)
            NettyLogUtil.d(TAG, "startClintWithSimpleThread::thread release")
        })
        thread.start()
    }

    internal fun startClientWithServer(host: String, port: Int) {
        NettyLogUtil.d(TAG, "startClientWithServer::mBindServer is: $mBindServer")
        ForegroundServer.setActionListener(this)
        mStartType = 2
        if (!mBindServer) {
            NettyLogUtil.d(TAG, "startClientWithServer::do bind server")
            this.mHost = host
            this.mPort = port
            bindClientServer(mContext)
        } else {
            startClientWithServerInternal(mContext, host, port)
        }
    }

    /**
     * @return true mean channel is ok, whether data send success or fail need wait asynchronous results
     */
    fun sendData(data: ByteArray): Boolean{
        if(mChannel == null || !mChannel?.isOpen!! || !mChannel?.isActive!!){
            return false
        }
        try {
            val buffer = Unpooled.buffer(data.size)
            buffer.writeBytes(data)
            mChannel!!.writeAndFlush(buffer).addListener {
                if (it.isSuccess) {
                    NettyLogUtil.d(TAG, "sendData::send data success")
                }else{
                    NettyLogUtil.d(TAG, "sendData::send data fail")
                }
            }
        }catch (e: Exception){
            e.printStackTrace()
            return false
        }
        return true
    }

    fun connect(host: String, port: Int) {
        NettyLogUtil.d(TAG, "connect::host is: $host and port is: $port")
        if (!NetworkUtils.isConnected(mContext)) {
            return
        }
        showConnectState(ConnectState.CONNECTING)
        if (nioEventLoopGroup == null) {
            nioEventLoopGroup = NioEventLoopGroup()
        }
        try {
            val f: ChannelFuture?
            val bootstrap = Bootstrap()
            bootstrap.group(nioEventLoopGroup)
            bootstrap.channel(NioSocketChannel::class.java)
            bootstrap.option(ChannelOption.TCP_NODELAY, true)
            bootstrap.option(ChannelOption.SO_KEEPALIVE, true)
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            bootstrap.handler(object : ChannelInitializer<SocketChannel>() {
                @Throws(Exception::class)
                override fun initChannel(ch: SocketChannel) {
                    byteToMessageDecoder = mIClientParameterCallBack?.getMessageDecoder()
                    localChannelAdapter = mIClientParameterCallBack?.getChannelAdapter()
                    if (byteToMessageDecoder == null || localChannelAdapter == null) {
                        throw IllegalArgumentException("connect->message decoder is null or channel adapter is null, please check")
                    }
                    localChannelAdapter?.apply {
                        mINotifyProxyStateChange = byteToMessageDecoder
                        mINotifyClientCoreConnectState = this@ClientCore
                        mLocalIChannelChange = mIChannelChange
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
            /**
             * how to use sync,syncUninterruptibly,wait and awaitUninterruptibly
             * @see io.netty.util.concurrent.DefaultPromise
             */
            f = bootstrap.connect(host, port).syncUninterruptibly()
            if (f.isDone) {
                NettyLogUtil.d(TAG, "connect::connect done")
                if (f.isSuccess) {
                    NettyLogUtil.d(TAG, "connect::connect success")
                    f.channel().closeFuture().sync()
                } else {
                    NettyLogUtil.d(TAG, "connect::connect fail")
                    f.cause().printStackTrace()
                    if (f.channel() != null) {
                        f.channel().close()
                    }
                }
            } else {
                NettyLogUtil.d(TAG, "connect::connect not done")
                f.cause().printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            NettyLogUtil.d(TAG, "do finally when channel close")
            showConnectState(ConnectState.CONNECT_RELEASE)
            closeConnectInternal(host, port)
        }
    }

    fun closeConnect() {
        NettyLogUtil.d(TAG, "closeConnect")
        mReconnectNum = 0
        stopAutoReconnect = true
        doCloseConnect()
    }

    fun checkConnectState(tag: String): Boolean {
        if (mChannel != null && mChannel!!.isOpen && mChannel!!.isActive) {
            NettyLogUtil.d(TAG, "checkConnectState::connect ok check tag is: $tag")
            return true
        }
        NettyLogUtil.d(TAG, "checkConnectState::connect bad check tag is: $tag")
        return false
    }

    fun reConnectServer(host: String, port: Int, closeAutoReconnect: Boolean) {
        val checkConnectState = checkConnectState("reConnectServer")
        NettyLogUtil.d(TAG, "reConnectServer::checkConnectState is: $checkConnectState")
        if (!checkConnectState) {
            stopAutoReconnect = closeAutoReconnect
            when (mStartType) {
                1 -> {
                    startClintWithSimpleThread(host, port)
                }
                2 -> {
                    startClientWithServer(host, port) //restart internal for user
                }
            }
        }
    }

    fun resetClientListener(
        context: Context?,
        iClientParameterCallBack: IClientParameterCallBack?,
        iChannelChange: LocalChannelAdapter.IChannelChange?
    ) {
        this.mContext = context
        this.mIClientParameterCallBack = iClientParameterCallBack
        localChannelAdapter?.apply {
            mLocalIChannelChange = iChannelChange
        }
    }

    fun removeClientListener() {
        mContext = null
        this.mIClientParameterCallBack = null
        this.mIChannelChange = null
        localChannelAdapter?.apply {
            mLocalIChannelChange = null
        }
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
        NettyLogUtil.d(TAG, "doCloseConnect")
        ForegroundServer.removeActionListener()
        this.localChannelAdapter?.mINotifyClientCoreConnectState = null
        this.localChannelAdapter?.mINotifyProxyStateChange = null
        this.nioEventLoopGroup?.shutdownGracefully()
        this.nioEventLoopGroup = null
        this.mChannel?.close()
        this.mChannel = null
        localChannelAdapter?.doubleCheckCloseChannel()
    }

    private fun doReconnect(host: String, port: Int) {
        if (mAutoReconnect && NetworkUtils.isConnected(mContext)) {
            if (mReconnectNum == mAutoReconnectFrequency) {
                //if current retry time == max frequency, stop reconnect
                NettyLogUtil.d(TAG, "doReconnect::stop retry")
                mReconnectNum = 0
                return
            } else {
                mHandler?.postDelayed({
                    if (!stopAutoReconnect) {
                        mReconnectNum++
                        NettyLogUtil.d(TAG, "doReconnect::current retry num is: $mReconnectNum")
                        when (mStartType) {
                            1 -> {
                                startClintWithSimpleThread(host, port)
                            }
                            2 -> {
                                startClientWithServer(
                                    host,
                                    port
                                ) //restart internal for auto reconnect
                            }
                        }
                    } else {
                        mReconnectNum = 0
                        NettyLogUtil.d(TAG, "doReconnect::stop reconnect by stop")
                    }
                }, mAutoReconnectIntervalTime)
            }
        }
    }

    private fun startClientWithServerInternal(context: Context?, host: String?, port: Int?) {
        if (context == null || host == null || port == null) {
            NettyLogUtil.e(TAG, "startClientWithServerInternal::start error please check param")
            return
        }
        ForegroundServer.startClientWithServer(context, host, port)
    }

    private fun bindClientServer(context: Context?) {
        if (context == null) {
            NettyLogUtil.e(TAG, "bindClientServer::context is null")
            return
        }
        if (mServiceConnection == null) {
            mServiceConnection = CustomServiceConnection()
        }
        val intent = Intent(context, ForegroundServer::class.java)
        context.applicationContext.bindService(
            intent,
            mServiceConnection!!,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun showConnectState(state: Int) {
        mHandler?.post {
            localChannelAdapter?.mLocalIChannelChange?.channelStateChange(
                localChannelAdapter?.mSimpleProxy,
                connectProxyState = false,
                connectTargetState = false,
                connectStateCode = state
            )
        }
    }

    private inner class CustomServiceConnection : ServiceConnection {

        override fun onServiceDisconnected(name: ComponentName?) {
            NettyLogUtil.d(TAG, "onServiceDisconnected")
            mBindServer = false
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            NettyLogUtil.d(TAG, "onServiceConnected")
            mBindServer = true
            val client: ForegroundServer.GetNettyClient = service as ForegroundServer.GetNettyClient
            val server = client.getServer()
            server.startForegroundNotify()
            startClientWithServerInternal(mContext, mHost, mPort) //services connected
        }
    }


    override fun notifyClientCoreConnectSuccess(channel: Channel?) {
        NettyLogUtil.d(TAG, "notifyClientCoreConnectSuccess::channel state is: ${channel?.isOpen}")
        mReconnectNum = 0 //when connect target success, reset it
        mChannel = channel
    }

    override fun notifyClientCoreProxyAuthError() {
        NettyLogUtil.d(TAG, "notifyClientCoreProxyAuthError::stop auto reconnect")
        mReconnectNum = 0
        stopAutoReconnect =
            true //if proxy auth error by proxy server, stop reconnect, and notify user by IChannelChange.channelStateChange
    }

    override fun actionConnect(host: String, port: Int) {
        NettyLogUtil.d(TAG, "actionConnect::host is: $host and port is: $port")
        connect(host, port)
    }

    override fun actionCheckConnect(): Boolean {
        return checkConnectState("actionCheckConnect")
    }

    interface IClientParameterCallBack {
        fun getMessageDecoder(): LocalByteToMessageDecoder
        fun getChannelAdapter(): LocalChannelAdapter
    }

}