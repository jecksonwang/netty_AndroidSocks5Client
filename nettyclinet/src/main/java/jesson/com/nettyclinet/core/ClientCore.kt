package jesson.com.nettyclinet.core

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
import jesson.com.nettyclinet.channeladapter.LocalChannelAdapter
import jesson.com.nettyclinet.decode.LocalByteToMessageDecoder
import jesson.com.nettyclinet.utils.LogUtil
import jesson.com.nettyclinet.utils.NetworkUtils
import java.util.concurrent.TimeUnit

class ClientCore {

    companion object {
        const val TAG = "ClientCore"
    }

    private var mContext: Context? = null
    private var mChannel: Channel? = null
    private var mHandler: Handler? = null
    private var mIGetT: IGetT? = null

    var mReadPingTimeOut: Long = 20000
    var mWritePingTimeOut: Long = 20000
    var mAllPingTimeOut: Long = 0

    var byteToMessageDecoder: LocalByteToMessageDecoder? = null
    var localChannelAdapter: LocalChannelAdapter? = null
    var nioEventLoopGroup: NioEventLoopGroup? = null

    var mAutoReconnect: Boolean = true
    var mAutoReconnectFrequency: Int = 5
    var mAutoReconnectIntervalTime: Long = 2000
    private var mReconnectNum = 0 //Current number of reconnections

    constructor(ctx: Context, iGetT: IGetT) {
        this.mContext = ctx
        this.mIGetT = iGetT
        mHandler = Handler(Looper.getMainLooper())
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
                    byteToMessageDecoder = mIGetT?.getDecoder()
                    localChannelAdapter = mIGetT?.getAdapter()
                    if (byteToMessageDecoder == null || localChannelAdapter == null) {
                        throw IllegalArgumentException("connect->message decoder is null or channel adapter is null, please check")
                    }
                    localChannelAdapter?.apply {
                        mINotifyProxyStateChange = byteToMessageDecoder
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
                    mReconnectNum = 0 //when connect success reset it
                    mChannel = f.channel()
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
            localChannelAdapter?.mIChannelChange?.channelStateChange(mChannel, false)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            LogUtil.d(TAG, "do finally when channel close")
            localChannelAdapter?.mINotifyProxyStateChange = null
            nioEventLoopGroup?.shutdownGracefully()
            nioEventLoopGroup = null
            mChannel = null
            doReconnect(host, port)
        }
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
                    mReconnectNum++
                    LogUtil.d(TAG, "doReconnect::current retry num is: $mReconnectNum")
                    connect(host, port)
                }, mAutoReconnectIntervalTime)
            }
        }
    }

    interface IGetT {
        fun getDecoder(): LocalByteToMessageDecoder
        fun getAdapter(): LocalChannelAdapter
    }

}