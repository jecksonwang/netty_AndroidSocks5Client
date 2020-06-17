package cn.jesson.nettyclient

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.jesson.nettyclient.channeladapter.LocalChannelAdapter
import cn.jesson.nettyclient.core.ClientCore
import cn.jesson.nettyclient.decode.LocalByteToMessageDecoder
import cn.jesson.nettyclient.decode.Socks5LineBasedFrameDecoder
import cn.jesson.nettyclient.utils.ConnectState
import cn.jesson.nettyclient.utils.LogUtil
import cn.jesson.nettyclient.utils.StartClientUtils
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), LocalChannelAdapter.IChannelChange,
    ClientCore.IClientParameterCallBack {

    private var mClientCore: ClientCore? = null

    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        LogUtil.d(TAG, "====================onCreate====================")
        server_state.text = String.format(resources.getString(R.string.server_state), "disconnect")
        mClientCore = StartClientUtils.getInstance()
            .startClientWithServer(application, this, this, "109.74.144.130", 1080, false)
        close_connect.setOnClickListener {
            mClientCore?.closeConnect()
        }
        reconnect.setOnClickListener {
            mClientCore?.reConnectServer("109.74.144.130", 1080)
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
        mClientCore?.resetClientListener(this, this, this)
        val checkConnectState = mClientCore?.checkConnectState(TAG)
        if(checkConnectState != null && checkConnectState){
            server_state.text = String.format(resources.getString(R.string.server_state), "connect")
        }
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
        mClientCore?.removeClientListener()
    }

    override fun channelStateChange(
        openProxy: Boolean?,
        connectProxyState: Boolean,
        connectTargetState: Boolean,
        connectStateCode: Int) {
        LogUtil.d(TAG, "channelStateChange::openProxy is: $openProxy and connectProxyState is: $connectProxyState " +
                "connect is: $connectTargetState and connect state code is: $connectStateCode")
        if (connectTargetState) {
            LogUtil.d(TAG, "channelStateChange::show connect")
            server_state.text = String.format(resources.getString(R.string.server_state), "connect")
        } else {
            if(connectStateCode == ConnectState.CONNECTING){
                LogUtil.d(TAG, "channelStateChange::show connecting")
                server_state.text =
                    String.format(resources.getString(R.string.server_state), "connecting")
            }else{
                LogUtil.d(TAG, "channelStateChange::show disconnect")
                server_state.text =
                    String.format(resources.getString(R.string.server_state), "disconnect")
            }
        }
    }

    override fun channelDataChange(msg: ByteArray?) {
    }

    override fun channelReadIdle() {
        LogUtil.d(TAG, "======READER_IDLE======")
    }

    override fun channelWriteIdle() {
        LogUtil.d(TAG, "======READER_IDLE======")
    }

    override fun channelAllIdle() {
        LogUtil.d(TAG, "======READER_IDLE======")
    }

    override fun channelException(cause: Throwable?) {
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
        return LocalChannelAdapter().apply {
            mSimpleProxy = true
            mTargetIP = "x.x.x.x" //add your real target ip
            mTargetPort = 1      //add your real target port
        }
    }

}
