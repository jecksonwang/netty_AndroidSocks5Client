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
import jesson.com.nettyclinet.utils.NetworkUtils
import java.util.concurrent.TimeUnit

class ClientCore {

    private var mContext: Context? = null
    private var mChannel: Channel? = null

    var mOpenProxy: Boolean = false
    var mReadPingTimeOut: Long = 20000
    var mWritePingTimeOut: Long = 20000
    var mAllPingTimeOut: Long = 0

    private var byteToMessageDecoder: ByteToMessageDecoder? = null
    private var localChannelAdapter: LocalChannelAdapter? = null
    private var nioEventLoopGroup: NioEventLoopGroup? = null

    constructor(ctx: Context) {
        this.mContext = ctx
    }

    private fun connect(host: String, port: Int) {
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
                if (f.isSuccess) {
                    mChannel = f.channel()
                    f.channel().closeFuture().sync()
                } else {
                    if (f.channel() != null) {
                        f.channel().close()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            nioEventLoopGroup?.shutdownGracefully()
            nioEventLoopGroup = null
        }
    }

    inner class ClientCoreBuilder {

        private var clientCore: ClientCore? = null

        constructor(ctx: Context){
            clientCore = ClientCore(ctx)
        }

        fun setProxy(openProxy: Boolean): ClientCore? {
            clientCore?.mOpenProxy = openProxy
            return clientCore
        }

        fun setFrameDecoder(decoder: ByteToMessageDecoder): ClientCore? {
            clientCore?.byteToMessageDecoder = decoder
            return clientCore
        }

        fun setChannelAdapter(adapter: LocalChannelAdapter):ClientCore?{
            clientCore?.localChannelAdapter = adapter
            return clientCore
        }

        fun build(): ClientCore? {
            return clientCore
        }
    }

    interface IProxyStateChange {
        fun proxyStateChange(state: Boolean)
    }

}