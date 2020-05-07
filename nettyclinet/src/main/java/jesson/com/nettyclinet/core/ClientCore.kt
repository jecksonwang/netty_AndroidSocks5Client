package jesson.com.nettyclinet.core

import android.content.Context
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.timeout.IdleStateHandler
import jesson.com.nettyclinet.channeladapter.LocalChannelAdapter
import jesson.com.nettyclinet.utils.LogUtil
import jesson.com.nettyclinet.utils.NetworkUtils
import java.util.concurrent.TimeUnit

class ClientCore {

    companion object{
        const val TAG = "ClientCore"
    }

    private var mContext: Context? = null
    private var mChannel: Channel? = null

    var mOpenProxy: Boolean = false
    var mReadPingTimeOut: Long = 20000
    var mWritePingTimeOut: Long = 20000
    var mAllPingTimeOut: Long = 0

    var byteToMessageDecoder: ByteToMessageDecoder? = null
    var localChannelAdapter: LocalChannelAdapter? = null
    var nioEventLoopGroup: NioEventLoopGroup? = null

    constructor(ctx: Context) {
        this.mContext = ctx
    }

    fun connect(host: String, port: Int) {
        LogUtil.d(TAG, "connect::host is: $host and port is: $port")
        if (!NetworkUtils.isConnected(mContext)) {
            return
        }
        if (nioEventLoopGroup == null) {
            nioEventLoopGroup = NioEventLoopGroup()
        } else {

        }
        try {
            var f: ChannelFuture? = null
            val bootstrap = Bootstrap()
            bootstrap.channel(NioSocketChannel::class.java)
            bootstrap.option(ChannelOption.TCP_NODELAY, true)
            bootstrap.option(ChannelOption.SO_KEEPALIVE, true)
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            bootstrap.group(nioEventLoopGroup)
            bootstrap.handler(object : ChannelInitializer<SocketChannel>() {
                @Throws(Exception::class)
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(byteToMessageDecoder)
                    ch.pipeline().addLast(
                        "IdleStateHandler",
                        IdleStateHandler(
                            mReadPingTimeOut,
                            mWritePingTimeOut,
                            mAllPingTimeOut,
                            TimeUnit.MILLISECONDS
                        )
                    )
                    ch.pipeline()
                        .addLast(localChannelAdapter)
                }
            })
            f = bootstrap.connect(host, port).awaitUninterruptibly()
            if (f.isDone) {
                LogUtil.d(TAG, "connect::connect done")
                if (f.isSuccess) {
                    LogUtil.d(TAG, "connect::connect success")
                    mChannel = f.channel()
                    f.channel().closeFuture().sync()
                } else {
                    LogUtil.d(TAG, "connect::connect fail")
                    if (f.channel() != null) {
                        f.channel().close()
                    }
                }
            }else{
                LogUtil.d(TAG, "connect::connect not done")
            }
            localChannelAdapter?.mIChannelChange?.channelStateChange(mChannel)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            nioEventLoopGroup?.shutdownGracefully()
            nioEventLoopGroup = null
        }
    }

}