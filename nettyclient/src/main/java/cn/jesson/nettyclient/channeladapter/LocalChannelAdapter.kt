package cn.jesson.nettyclient.channeladapter

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import cn.jesson.nettyclient.utils.Constants
import cn.jesson.nettyclient.utils.ConnectState
import cn.jesson.nettyclient.utils.NettyLogUtil
import cn.jesson.nettyclient.utils.Socks5Utils
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerAdapter
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import java.net.InetAddress

class LocalChannelAdapter : ChannelHandlerAdapter() {
    /**
     *  if use socks5,open simpleProxy,LocalChannelAdapter can auto handle socks5,and you must set mTargetIP,mTargetPort
     *  if socks5 need auth,you also need set name and password,if close simpleProxy you need do it by yourself
     */
    var mSimpleProxy: Boolean = false
    var mTargetIP: String? = null
    var mTargetPort: Int = 0
    var mAuthName: String? = null
    var mAuthPassword: String? = null

    internal var mLocalIChannelChange: IChannelChange? = null
    internal var mINotifyProxyStateChange: INotifyProxyStateChange? = null
    internal var mINotifyClientCoreConnectState: INotifyClientCoreConnectState? = null

    private var mHandler: Handler? = null
    private var mInternalChannel: Channel? = null

    init {
        mHandler = Handler(Looper.getMainLooper())
    }

    companion object {
        const val TAG = "LocalChannelAdapter"
    }

    private var mProxyRequest: Int? = Constants.PROXY_REQUEST_NONE

    override fun channelActive(ctx: ChannelHandlerContext?) {
        mInternalChannel = ctx?.channel()
        mHandler?.post {
            if (mSimpleProxy) {
                NettyLogUtil.d(TAG, "send init proxy request")
                mProxyRequest = Constants.PROXY_REQUEST_INIT
                val data: ByteArray = Socks5Utils.getInstance().buildProxyInitInfo()
                val buf = Unpooled.buffer(data.size)
                buf.writeBytes(data)
                ctx?.writeAndFlush(buf)
            } else {
                mINotifyClientCoreConnectState?.notifyClientCoreConnectSuccess(ctx?.channel())
            }
            mLocalIChannelChange?.channelStateChange(
                mSimpleProxy,
                mSimpleProxy,
                !mSimpleProxy,
                if(mSimpleProxy) ConnectState.CONNECTING else ConnectState.CONNECTED
            )
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        mHandler?.post {
            mLocalIChannelChange?.channelStateChange(
                mSimpleProxy, connectProxyState = false, connectTargetState = false,
                connectStateCode = ConnectState.ERROR_CHANNEL_INACTIVE
            )
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val buf = msg as ByteBuf
        try {
            val data = ByteArray(buf.readableBytes())
            buf.readBytes(data)
            NettyLogUtil.d(TAG, "channelRead::receive data is: " + data.contentToString())
            if (data.isEmpty()) {
                NettyLogUtil.d(TAG, "channelRead::receive null data")
            } else {
                if (mSimpleProxy) {
                    when (mProxyRequest) {
                        Constants.PROXY_REQUEST_INIT -> {
                            NettyLogUtil.d(TAG, "channelRead::PROXY_REQUEST_INIT")
                            if (data.size == 2 && data[0].toInt() == Constants.PROXY_SOCKS_VERION) {
                                if (data[1].toInt() == Constants.PROXY_SOCKS_NO_AUTH) {
                                    NettyLogUtil.d(TAG, "channelRead::PROXY_SOCKS_NO_AUTH")
                                    mProxyRequest = Constants.PROXY_REQUEST_CONNECT_TARGET_HOST
                                    val hostIP: String? = getHostIP(mTargetIP)
                                    val proxyData: ByteArray? = Socks5Utils.getInstance()
                                        .buildProxySendConnectInfo(hostIP, mTargetPort.toByte())
                                    if (proxyData != null) {
                                        val bufHead = Unpooled.buffer(proxyData.size)
                                        bufHead.writeBytes(proxyData)
                                        ctx?.writeAndFlush(bufHead)
                                    } else {
                                        NettyLogUtil.d(TAG, "channelRead::proxy->send prxoy error")
                                        mHandler?.post {
                                            mLocalIChannelChange?.channelStateChange(
                                                mSimpleProxy,
                                                connectProxyState = false,
                                                connectTargetState = false,
                                                connectStateCode = ConnectState.ERROR_PROXY_CONNECT_INFO_NONE
                                            )
                                        }
                                    }
                                } else if (data[1].toInt() == Constants.PROXY_SOCKS_AUTH) {
                                    NettyLogUtil.d(TAG, "channelRead::PROXY_SOCKS_AUTH")
                                    mProxyRequest = Constants.PROXY_REQUEST_AUTH_LOGIN
                                    if (TextUtils.isEmpty(mAuthName) || TextUtils.isEmpty(mAuthPassword)) {
                                        NettyLogUtil.d(TAG, "channelRead::auth name or pwd is null")
                                        mHandler?.post {
                                            mINotifyClientCoreConnectState?.notifyClientCoreProxyAuthError()
                                            mLocalIChannelChange?.channelStateChange(
                                                mSimpleProxy,
                                                connectProxyState = false,
                                                connectTargetState = false,
                                                connectStateCode = ConnectState.ERROR_AUTH_NAMEPASSWORD_INVALID
                                            )
                                        }
                                    } else {
                                        val authInfo: ByteArray? = Socks5Utils.getInstance()
                                            .buildProxyAuthInfo(mAuthName, mAuthPassword)
                                        if (authInfo != null) {
                                            val bufAuthInfo = Unpooled.buffer(authInfo.size)
                                            bufAuthInfo.writeBytes(authInfo)
                                            ctx?.writeAndFlush(bufAuthInfo)
                                        } else {
                                            NettyLogUtil.d(TAG, "channelRead::proxy->send auth error")
                                            mHandler?.post {
                                                mLocalIChannelChange?.channelStateChange(
                                                    mSimpleProxy,
                                                    connectProxyState = true,
                                                    connectTargetState = false,
                                                    connectStateCode = ConnectState.ERROR_PROXY_AUTH_INFO_NONE
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    NettyLogUtil.e(TAG, "channelRead::UNKNOWN_PROXY_AUTH")
                                }
                            }
                        }
                        Constants.PROXY_REQUEST_AUTH_LOGIN -> {
                            NettyLogUtil.d(TAG, "channelRead::PROXY_REQUEST_AUTH_LOGIN")
                            if (data.size == 2 && data[1].toInt() == Constants.PROXY_AUTH_SUCCESS) {
                                NettyLogUtil.d(TAG, "channelRead::PROXY_REQUEST_AUTH_LOGIN do login")
                                mProxyRequest = Constants.PROXY_REQUEST_CONNECT_TARGET_HOST
                                val hostIP = getHostIP(mTargetIP)
                                val proxySendConnectInfo: ByteArray? = Socks5Utils.getInstance()
                                    .buildProxySendConnectInfo(hostIP, mTargetPort.toByte())
                                if (proxySendConnectInfo != null) {
                                    val bufHead = Unpooled.buffer(proxySendConnectInfo.size)
                                    bufHead.writeBytes(proxySendConnectInfo)
                                    ctx!!.writeAndFlush(bufHead)
                                } else {
                                    NettyLogUtil.d(TAG, "channelRead::proxy->do login send prxoy error")
                                    mHandler?.post {
                                        mLocalIChannelChange?.channelStateChange(
                                            mSimpleProxy,
                                            connectProxyState = true,
                                            connectTargetState = false,
                                            connectStateCode = ConnectState.ERROR_PROXY_CONNECT_INFO_NONE
                                        )
                                    }
                                }
                            } else {
                                NettyLogUtil.d(TAG, "channelRead::proxy->auth fail")
                                mHandler?.post {
                                    mINotifyClientCoreConnectState?.notifyClientCoreProxyAuthError()
                                    mLocalIChannelChange?.channelStateChange(
                                        mSimpleProxy,
                                        connectProxyState = true,
                                        connectTargetState = false,
                                        connectStateCode = ConnectState.ERROR_PROXY_AUTH_FAIL
                                    )
                                }
                            }
                        }
                        Constants.PROXY_REQUEST_CONNECT_TARGET_HOST -> {
                            NettyLogUtil.d(TAG, "channelRead::PROXY_REQUEST_CONNECT_TARGET_HOST")
                            val result = data[1].toInt()
                            if (data[0].toInt() == Constants.PROXY_SOCKS_VERION && result == Constants.PROXY_CONNECT_SUCCESS) {
                                NettyLogUtil.d(TAG, "channelRead::PROXY_CONNECT_SUCCESS")
                                mHandler?.post {
                                    mINotifyClientCoreConnectState?.notifyClientCoreConnectSuccess(ctx?.channel())
                                    mINotifyProxyStateChange?.notifyProxyStateChange(false)
                                    mLocalIChannelChange?.channelStateChange(
                                        mSimpleProxy,
                                        connectProxyState = true,
                                        connectTargetState = true,
                                        connectStateCode = ConnectState.CONNECTED
                                    )
                                }
                            } else {
                                NettyLogUtil.d(TAG, "channelRead::PROXY_CONNECT_FAIL, fail info is: $result")
                                mHandler?.post {
                                    mLocalIChannelChange?.channelStateChange(
                                        mSimpleProxy,
                                        connectProxyState = true,
                                        connectTargetState = false,
                                        connectStateCode = result
                                    )
                                }
                            }
                        }
                        else -> {
                            NettyLogUtil.e(TAG, "channelRead::UNKNOWN PROXY")
                        }
                    }
                } else {
                    mHandler?.post {
                        mLocalIChannelChange?.channelDataChange(data)
                    }
                }
            }
        }finally {
            buf.release()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        mHandler?.post {
            mLocalIChannelChange?.channelException(cause)
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        if (evt is IdleStateEvent) {
            when {
                evt.state() == IdleState.READER_IDLE -> {
                    NettyLogUtil.d(TAG, "======READER_IDLE======")
                    mHandler?.post {
                        mLocalIChannelChange?.channelReadIdle()
                    }
                }
                evt.state() == IdleState.WRITER_IDLE -> {
                    NettyLogUtil.d(TAG, "======WRITER_IDLE======")
                    mHandler?.post {
                        mLocalIChannelChange?.channelWriteIdle()
                    }
                }
                evt.state() == IdleState.ALL_IDLE -> {
                    NettyLogUtil.d(TAG, "======ALL_IDLE======")
                    mHandler?.post {
                        mLocalIChannelChange?.channelAllIdle()
                    }
                }
            }
        }
    }

    interface IChannelChange {
        /**
         * @param openProxy whether open proxy function
         * @param connectProxyState if open proxy function, need check proxy connect state first, or the state always false
         * @param connectTargetState whether connect the target server, use this parameter to determine whether to complete the connection
         * @param connectStateCode see below
         * @see cn.jesson.nettyclient.utils.ConnectState
         */
        fun channelStateChange(
            openProxy: Boolean?,
            connectProxyState: Boolean,
            connectTargetState: Boolean,
            connectStateCode: Int
        )

        fun channelDataChange(msg: ByteArray?)
        fun channelException(cause: Throwable?)
        fun channelReadIdle()
        fun channelWriteIdle()
        fun channelAllIdle()

    }

    internal interface INotifyProxyStateChange {
        fun notifyProxyStateChange(state: Boolean) //state: true means proxy connecting, false means proxy connected
    }

    internal interface INotifyClientCoreConnectState {
        fun notifyClientCoreConnectSuccess(channel: Channel?)
        fun notifyClientCoreProxyAuthError()
    }

    internal fun doubleCheckCloseChannel(){
        if(mInternalChannel != null && mInternalChannel!!.isOpen){
            mInternalChannel?.close()
        }
        mInternalChannel = null
    }

    private fun getHostIP(addressHost: String?): String? {
        try {
            if (TextUtils.isEmpty(addressHost)) {
                throw IllegalArgumentException("target ip need not null when open simple proxy")
            }
            val address = InetAddress.getByName(addressHost)
            val hostAddress = address.hostAddress
            NettyLogUtil.d(TAG, "getHostIP::connect server ip is: $hostAddress")
            return hostAddress
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

}