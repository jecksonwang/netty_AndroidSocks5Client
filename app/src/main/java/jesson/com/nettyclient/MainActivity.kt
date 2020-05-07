package jesson.com.nettyclient

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import jesson.com.nettyclinet.channeladapter.LocalChannelAdapter
import jesson.com.nettyclinet.core.ClientCore
import jesson.com.nettyclinet.decode.Socks5DelimiterBasedFrameDecoder
import jesson.com.nettyclinet.utils.LogUtil

class MainActivity : AppCompatActivity(), LocalChannelAdapter.IChannelChange {

    companion object{
        const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val thread = Thread(Runnable {
            LogUtil.d(TAG, "thread run")
            val clientCore = ClientCore(this).apply {
                val buf: ByteBuf = Unpooled.copiedBuffer(TAG.toByteArray())
                byteToMessageDecoder = Socks5DelimiterBasedFrameDecoder.Builder(100000, buf)
                    .setProxyState(true)
                    .build()
                localChannelAdapter = LocalChannelAdapter(this@MainActivity).apply {
                    mSimpleProxy = true
                    mTargetIP = "test.com" //add your real target ip
                    mTargetPort = 123      //add your real target port
                    mIProxyStateChange = byteToMessageDecoder as Socks5DelimiterBasedFrameDecoder
                }
            }
            clientCore.connect("proxy.text.com", 1080) //add your real proxy ip and port
        })
        thread.start()
    }

    override fun channelStateChange(channel: Channel?) {
        val open = channel?.isOpen
        val active = channel?.isActive
        LogUtil.d(TAG, "open is: $open and active is: $active")
    }

    override fun channelDataChange(msg: ByteArray?) {
    }

    override fun channelEventTriggered(evt: Any?) {
        if (evt is IdleStateEvent) {
            when {
                evt.state() == IdleState.READER_IDLE -> {
                    LogUtil.d(TAG, "======READER_IDLE======")
                }
                evt.state() == IdleState.WRITER_IDLE -> {
                    LogUtil.d(TAG, "======WRITER_IDLE======")
                }
                evt.state() == IdleState.ALL_IDLE -> {
                    LogUtil.d(TAG, "======ALL_IDLE======")
                }
            }
        }
    }

    override fun channelException(channel: Channel?, cause: Throwable?) {
        LogUtil.d(TAG, "error is: ${cause?.printStackTrace()}")
    }
}
