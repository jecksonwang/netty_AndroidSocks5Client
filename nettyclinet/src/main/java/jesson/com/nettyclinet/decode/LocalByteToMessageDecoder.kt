package jesson.com.nettyclinet.decode

import io.netty.handler.codec.ByteToMessageDecoder
import jesson.com.nettyclinet.channeladapter.LocalChannelAdapter
import jesson.com.nettyclinet.utils.LogUtil

abstract class LocalByteToMessageDecoder : ByteToMessageDecoder(), LocalChannelAdapter.INotifyProxyStateChange{

    override fun notifyProxyStateChange(state: Boolean) {
        LogUtil.d("LocalByteToMessageDecoder", "proxyStateChange::state is: $state")
    }
}