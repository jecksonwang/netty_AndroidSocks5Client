package cn.jesson.nettyclient

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.netty.channel.Channel
import cn.jesson.nettyclient.channeladapter.LocalChannelAdapter
import cn.jesson.nettyclient.core.ClientCore
import cn.jesson.nettyclient.decode.LocalByteToMessageDecoder
import cn.jesson.nettyclient.decode.Socks5LineBasedFrameDecoder
import cn.jesson.nettyclient.utils.LogUtil
import cn.jesson.nettyclient.utils.StartClientUtils
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), LocalChannelAdapter.IChannelChange, ClientCore.IGetNettyClientParameter {

    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        LogUtil.d(TAG, "====================onCreate====================")
        server_state.text = String.format(resources.getString(R.string.server_state), "disconnect")
        val clientCore = StartClientUtils.getInstance().startClientWithServer(this, this, "5.252.161.48", 1080)
        close_connect.setOnClickListener {
            clientCore.closeConnect()
        }
        reconnect.setOnClickListener{
            clientCore.reConnectServer("5.252.161.48", 1080)
        }
    }

    override fun onStart() {
        super.onStart()
        LogUtil.d(TAG, "====================onStart====================")
    }

    override fun onRestart() {
        super.onRestart()
        LogUtil.d(TAG, "====================onRestart====================")
    }

    override fun onResume() {
        super.onResume()
        LogUtil.d(TAG, "====================onResume====================")
    }

    override fun onPause() {
        super.onPause()
        LogUtil.d(TAG, "====================onPause====================")
    }

    override fun onStop() {
        super.onStop()
        LogUtil.d(TAG, "====================onStop====================")
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtil.d(TAG, "====================onDestroy====================")

    }

    override fun channelStateChange(openProxy: Boolean?, connectProxyState: Boolean, connectTargetState: Boolean, errorCode: Int) {
        LogUtil.d(TAG, "openProxy is: $openProxy and connectProxyState is: $connectProxyState connect is: $connectTargetState and error code is: $errorCode")
        if(connectTargetState){
            server_state.text = String.format(resources.getString(R.string.server_state), "connect")
        }else{
            server_state.text = String.format(resources.getString(R.string.server_state), "disconnect")
        }
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
            mTargetPort = 1      //add your real target port
        }
    }

}
