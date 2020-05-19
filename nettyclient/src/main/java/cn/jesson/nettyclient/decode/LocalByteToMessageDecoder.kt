package cn.jesson.nettyclient.decode

import io.netty.handler.codec.ByteToMessageDecoder
import cn.jesson.nettyclient.channeladapter.LocalChannelAdapter

abstract class LocalByteToMessageDecoder : ByteToMessageDecoder(), LocalChannelAdapter.INotifyProxyStateChange{

    override fun notifyProxyStateChange(state: Boolean) {
    }
}