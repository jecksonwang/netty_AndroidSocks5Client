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
import jesson.com.nettyclinet.decode.Socks5LineBasedFrameDecoder
import jesson.com.nettyclinet.utils.LogUtil
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), LocalChannelAdapter.IChannelChange, ClientCore.IGetNettyClientParameter {

    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val clientCore = ClientCore(this, this)
        clientCore.startClintWithSimpleThread("5.252.161.48", 1080) //add your real proxy ip and port
        close_connect.setOnClickListener {
            clientCore.closeConnect()
        }
    }

    override fun channelStateChange(openProxy: Boolean?, connectProxyState: Boolean, connectTargetState: Boolean, errorCode: Int) {
        LogUtil.d(TAG, "openProxy is: $openProxy and connectProxyState is: $connectProxyState connect is: $connectTargetState and error code is: $errorCode")
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

        /*val buf: ByteBuf = Unpooled.copiedBuffer(TAG.toByteArray())
        return Socks5DelimiterBasedFrameDecoder.Builder(100000, buf)
            .setProxyState(true)
            .build()*/

        return Socks5LineBasedFrameDecoder.Builder(100000)
            .setProxyState(true)
            .build()
    }

    override fun getChannelAdapter(): LocalChannelAdapter {
        return LocalChannelAdapter(this@MainActivity).apply {
            mSimpleProxy = true
            mTargetIP = "x.x.x.x" //add your real target ip
            mTargetPort = 80      //add your real target port
        }
    }

}
