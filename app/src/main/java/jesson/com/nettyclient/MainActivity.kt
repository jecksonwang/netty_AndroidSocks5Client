package jesson.com.nettyclient

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import jesson.com.nettyclinet.channeladapter.LocalChannelAdapter
import jesson.com.nettyclinet.core.ClientCore
import jesson.com.nettyclinet.decode.LocalByteToMessageDecoder
import jesson.com.nettyclinet.decode.Socks5DelimiterBasedFrameDecoder
import jesson.com.nettyclinet.utils.LogUtil

class MainActivity : AppCompatActivity(), LocalChannelAdapter.IChannelChange, ClientCore.IGetNettyClientParameter {

    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val thread = Thread(Runnable {
            LogUtil.d(TAG, "thread run")
            val clientCore = ClientCore(this, this)
            clientCore.connect("xxx.xxx.xx.xx", 1080) //add your real proxy ip and port
        })
        thread.start()
    }

    override fun channelStateChange(channel: Channel?, openProxy: Boolean?, connectProxyState: Boolean, connectTargetState: Boolean) {
        val open = channel?.isOpen
        val active = channel?.isActive
        LogUtil.d(TAG, "open is: $open and active is: $active and openProxy is: $openProxy and connectProxyState is: $connectProxyState connect is: $connectTargetState")
    }

    override fun channelDataChange(msg: ByteArray?) {
    }

    override fun channelReadIdle() {
    }

    override fun channelWriteIdle() {
    }

    override fun channelAllIdle() {
    }

    override fun channelException(channel: Channel?, cause: Throwable?) {
        LogUtil.d(TAG, "error is: ${cause?.printStackTrace()}")
    }

    override fun getMessageDecoder(): LocalByteToMessageDecoder {
        val buf: ByteBuf = Unpooled.copiedBuffer(TAG.toByteArray())
        return Socks5DelimiterBasedFrameDecoder.Builder(100000, buf)
            .setProxyState(true)
            .build()
    }

    override fun getChannelAdapter(): LocalChannelAdapter {
        return LocalChannelAdapter(this@MainActivity).apply {
            mSimpleProxy = true
            mTargetIP = "xx.xx.xx" //add your real target ip
            mTargetPort = 123      //add your real target port
        }
    }

}
