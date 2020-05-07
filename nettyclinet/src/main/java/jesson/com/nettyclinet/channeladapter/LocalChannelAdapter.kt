package jesson.com.nettyclinet.channeladapter

import android.text.TextUtils
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerAdapter
import io.netty.channel.ChannelHandlerContext
import jesson.com.nettyclinet.utils.Constants
import jesson.com.nettyclinet.utils.LogUtil
import jesson.com.nettyclinet.utils.Socks5Utils
import java.net.InetAddress

class LocalChannelAdapter(
    private var mIChannelChange: IChannelChange
) : ChannelHandlerAdapter() {
    /**
     *  if use socks5,open simpleProxy,LocalChannelAdapter can auto handle socks5,and you must set mTargetIP,mTargetPort
     *  if socks5 need auth,you also need set name and password,otherwise you need do it by yourself
     */
    var mSimpleProxy: Boolean = false
    var mTargetIP: String? = null
    var mTargetPort: Int = 0
    var mAuthName: String? = null
    var mAuthPassword: String? = null

    companion object {
        const val TAG = "LocalChannelAdapter"
    }

    private var mProxyRequest: Int? = Constants.PROXY_REQUEST_NONE

    override fun channelActive(ctx: ChannelHandlerContext?) {
        mIChannelChange.channelStateChange(ctx?.channel())
        if (mSimpleProxy) {
            mProxyRequest = Constants.PROXY_REQUEST_INIT
            val data: ByteArray = Socks5Utils.getInstance().buildProxyInitInfo()
            val buf = Unpooled.buffer(data.size)
            LogUtil.d(TAG, "channelActive::do init proxy and data is: $data")
            buf.writeBytes(data)
            ctx!!.writeAndFlush(buf)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        mIChannelChange.channelStateChange(ctx?.channel())
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val buf = msg as ByteBuf
        val data = ByteArray(buf.readableBytes())
        buf.readBytes(data)
        LogUtil.d(TAG, "channelRead::receive data is: " + data.contentToString())
        if (mSimpleProxy) {
            when (mProxyRequest) {
                Constants.PROXY_REQUEST_INIT -> {
                    LogUtil.d(TAG, "channelRead::PROXY_REQUEST_INIT")
                    if (data.size == 2 && data[0].toInt() == Constants.PROXY_SOCKS_VERION) {
                        if (data[1].toInt() == Constants.PROXY_SOCKS_AUTH_NONE) {
                            LogUtil.d(TAG, "channelRead::PROXY_SOCKS_AUTH_NONE")
                            mProxyRequest = Constants.PROXY_REQUEST_CONNECT_TARGET_HOST
                            val hostIP: String? = getHostIP(mTargetIP)
                            val proxyData: ByteArray? = Socks5Utils.getInstance()
                                .buildProxySendConnectInfo(hostIP, mTargetPort.toByte())
                            if (proxyData != null) {
                                val bufHead = Unpooled.buffer(proxyData.size)
                                bufHead.writeBytes(proxyData)
                                ctx!!.writeAndFlush(bufHead)
                            } else {
                                LogUtil.d(TAG, "channelRead::proxy->send prxoy error")
                            }
                        } else if (data[1].toInt() == Constants.PROXY_SOCKS_AUTH_NAMEPASSWORD) {
                            LogUtil.d(TAG, "channelRead::PROXY_SOCKS_AUTH_NAMEPASSWORD")
                            mProxyRequest = Constants.PROXY_REQUEST_AUTH_LOGIN
                            val authInfo: ByteArray? = Socks5Utils.getInstance()
                                .buildProxyAuthInfo(mAuthName, mAuthPassword)
                            if (authInfo != null) {
                                val bufAuthInfo = Unpooled.buffer(authInfo.size)
                                bufAuthInfo.writeBytes(authInfo)
                                ctx!!.writeAndFlush(bufAuthInfo)
                            } else {
                                LogUtil.d(TAG, "channelRead::proxy->send auth error")
                            }
                        } else {
                            LogUtil.d(TAG, "channelRead::PRPXY_SOCKS_AUTH_ERROR")
                        }
                    } else {

                    }
                }
                Constants.PROXY_REQUEST_AUTH_LOGIN->{
                    LogUtil.d(TAG, "channelRead::PROXY_REQUEST_AUTH_LOGIN")
                    if (data.size == 2 && data[1].toInt() == Constants.PROXY_AUTH_SUCCESS) {
                        LogUtil.d(TAG, "channelRead::PROXY_REQUEST_AUTH_LOGIN do login")
                        mProxyRequest = Constants.PROXY_REQUEST_CONNECT_TARGET_HOST
                        val hostIP = getHostIP(mTargetIP)
                        val proxySendConnectInfo: ByteArray? = Socks5Utils.getInstance().buildProxySendConnectInfo(hostIP, mTargetPort.toByte())
                        if (proxySendConnectInfo != null) {
                            val bufHead = Unpooled.buffer(proxySendConnectInfo.size)
                            bufHead.writeBytes(proxySendConnectInfo)
                            ctx!!.writeAndFlush(bufHead)
                        } else {
                            LogUtil.d(TAG, "channelRead::proxy->do login send prxoy error")
                        }
                    } else {
                        LogUtil.d(TAG, "channelRead::proxy->auth error")
                    }
                }
                Constants.PROXY_REQUEST_CONNECT_TARGET_HOST->{
                    LogUtil.d(TAG, "channelRead::PROXY_REQUEST_CONNECT_TARGET_HOST")
                    if (data[0].toInt() == Constants.PROXY_SOCKS_VERION && data[1].toInt() == Constants.PROXY_CONNECT_SUCCESS) {
                        LogUtil.d(TAG, "channelRead::PROXY_CONNECT_SUCCESS")
                    } else {

                    }
                }
                else->{
                    LogUtil.d(TAG, "channelRead::UNKNOWN PROXY")
                }
            }
        } else {
            mIChannelChange.channelDataChange(data)
        }
    }

    /**
     *
     */
    private fun getHostIP(addressHost: String?): String? {
        try {
            if (TextUtils.isEmpty(addressHost)) {
                throw IllegalArgumentException("target ip need not null when open simple proxy")
            }
            val address = InetAddress.getByName(addressHost)
            val hostAddress = address.hostAddress
            LogUtil.d(TAG, "getHostIP::connect server ip is: $hostAddress")
            return hostAddress
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        mIChannelChange.channelException(ctx?.channel(), cause)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        mIChannelChange.channelEventTriggered(evt)
    }

    interface IChannelChange {
        fun channelStateChange(channel: Channel?)
        fun channelDataChange(msg: ByteArray?)
        fun channelEventTriggered(evt: Any?)
        fun channelException(channel: Channel?, cause: Throwable?)
    }

    inner class Builder {

        private var localAdapter: LocalChannelAdapter? = null

        /**
        private var AuthPassword: String? = null
         */
        constructor(mIChannelChange: IChannelChange) {
            localAdapter = LocalChannelAdapter(mIChannelChange)
        }

        fun setSimpleProxy(simpleProxy: Boolean): LocalChannelAdapter? {
            localAdapter?.mSimpleProxy = simpleProxy
            return localAdapter
        }

        fun setTargetIP(targetIP: String): LocalChannelAdapter? {
            localAdapter?.mTargetIP = targetIP
            return localAdapter
        }

        fun setTargetPort(targetPort: Int): LocalChannelAdapter? {
            localAdapter?.mTargetPort = targetPort
            return localAdapter
        }

        fun setAuthName(authName: String): LocalChannelAdapter? {
            localAdapter?.mAuthName = authName
            return localAdapter
        }

        fun setAuthPassword(authPassword: String): LocalChannelAdapter? {
            localAdapter?.mAuthPassword = authPassword
            return localAdapter
        }

    }

}