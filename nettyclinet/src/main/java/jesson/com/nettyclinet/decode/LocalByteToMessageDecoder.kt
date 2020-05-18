package jesson.com.nettyclinet.decode

import io.netty.handler.codec.ByteToMessageDecoder
import jesson.com.nettyclinet.channeladapter.LocalChannelAdapter

abstract class LocalByteToMessageDecoder : ByteToMessageDecoder(), LocalChannelAdapter.INotifyProxyStateChange{

    override fun notifyProxyStateChange(state: Boolean) {
    }
}