package cn.jesson.nettyclient.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.timeout.IdleStateHandler
import cn.jesson.nettyclient.channeladapter.LocalChannelAdapter
import cn.jesson.nettyclient.decode.LocalByteToMessageDecoder
import cn.jesson.nettyclient.utils.Error
import cn.jesson.nettyclient.utils.LogUtil
import cn.jesson.nettyclient.utils.NetworkUtils
import java.util.concurrent.TimeUnit

class ClientCore(ctx: Context, iGetNettyClientParameter: IGetNettyClientParameter) :
    LocalChannelAdapter.INotifyClientCoreConnectState {

    companion object {
        const val TAG = "ClientCore"
    }

    private var mContext: Context? = ctx
    private var mChannel: Channel? = null
    private var mHandler: Handler? = null
    private var mIGetNettyClientParameter: IGetNettyClientParameter? = iGetNettyClientParameter

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

    public fun startClintWithSimpleThread(host: String, port: Int) {
        val thread = Thread(Runnable {
            LogUtil.d(TAG, "thread run")
            connect(host, port)
            LogUtil.d(TAG, "thread run release")
        })
        thread.start()
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

    public fun closeConnect() {
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
                    if(!stopAutoReconnect){
                        mReconnectNum++
                        LogUtil.d(TAG, "doReconnect::current retry num is: $mReconnectNum")
                        startClintWithSimpleThread(host, port)
                    }else{
                        mReconnectNum = 0
                        LogUtil.d(TAG, "doReconnect::stop reconnect by stop")
                    }
                }, mAutoReconnectIntervalTime)
            }
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
        stopAutoReconnect = true //if proxy auth error by proxy server, stop reconnect, and notify user by IChannelChange.channelStateChange
    }

    interface IGetNettyClientParameter {
        fun getMessageDecoder(): LocalByteToMessageDecoder
        fun getChannelAdapter(): LocalChannelAdapter
    }

}