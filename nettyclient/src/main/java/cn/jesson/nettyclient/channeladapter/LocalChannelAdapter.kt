package cn.jesson.nettyclient.channeladapter

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerAdapter
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import cn.jesson.nettyclient.utils.Constants
import cn.jesson.nettyclient.utils.Error
import cn.jesson.nettyclient.utils.LogUtil
import cn.jesson.nettyclient.utils.Socks5Utils
import java.net.InetAddress

class LocalChannelAdapter(
    var mIChannelChange: IChannelChange?
) : ChannelHandlerAdapter() {
    /**
     *  if use socks5,open simpleProxy,LocalChannelAdapter can auto handle socks5,and you must set mTargetIP,mTargetPort
     *  if socks5 need auth,you also need set name and password,if close simpleProxy you need do it by yourself
     */
    var mSimpleProxy: Boolean = false
    var mTargetIP: String? = null
    var mTargetPort: Int = 0
    var mAuthName: String? = null
    var mAuthPassword: String? = null

    var mINotifyProxyStateChange: INotifyProxyStateChange? = null
    var mINotifyClientCoreConnectState: INotifyClientCoreConnectState? = null

    private var mHandler: Handler? = null

    init {
        mHandler = Handler(Looper.getMainLooper())
    }

    companion object {
        const val TAG = "LocalChannelAdapter"
    }

    private var mProxyRequest: Int? = Constants.PROXY_REQUEST_NONE

    override fun channelActive(ctx: ChannelHandlerContext?) {
        mIChannelChange?.channelStateChange(
            mSimpleProxy,
            mSimpleProxy,
            !mSimpleProxy,
            Error.NO_ERROR
        )
        if (mSimpleProxy) {
            mProxyRequest = Constants.PROXY_REQUEST_INIT
            val data: ByteArray = Socks5Utils.getInstance().buildProxyInitInfo()
            val buf = Unpooled.buffer(data.size)
            LogUtil.d(TAG, "channelActive::do init proxy and data is: ${data.contentToString()}")
            buf.writeBytes(data)
            ctx?.writeAndFlush(buf)
        } else {
            mINotifyClientCoreConnectState?.notifyClientCoreConnectSuccess(ctx?.channel())
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        mIChannelChange?.channelStateChange(
            mSimpleProxy, connectProxyState = false, connectTargetState = false,
            errorCode = Error.CHANNEL_INACTIVE
        )
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        val buf = msg as ByteBuf
        val data = ByteArray(buf.readableBytes())
        buf.readBytes(data)
        LogUtil.d(TAG, "channelRead::receive data is: " + data.contentToString())
        if (data.isEmpty()) {
            LogUtil.d(TAG, "channelRead::receive null data")
        } else {
            if (mSimpleProxy) {
                when (mProxyRequest) {
                    Constants.PROXY_REQUEST_INIT -> {
                        LogUtil.d(TAG, "channelRead::PROXY_REQUEST_INIT")
                        if (data.size == 2 && data[0].toInt() == Constants.PROXY_SOCKS_VERION) {
                            if (data[1].toInt() == Constants.PROXY_SOCKS_NO_AUTH) {
                                LogUtil.d(TAG, "channelRead::PROXY_SOCKS_NO_AUTH")
                                mProxyRequest = Constants.PROXY_REQUEST_CONNECT_TARGET_HOST
                                val hostIP: String? = getHostIP(mTargetIP)
                                val proxyData: ByteArray? = Socks5Utils.getInstance()
                                    .buildProxySendConnectInfo(hostIP, mTargetPort.toByte())
                                if (proxyData != null) {
                                    val bufHead = Unpooled.buffer(proxyData.size)
                                    bufHead.writeBytes(proxyData)
                                    ctx?.writeAndFlush(bufHead)
                                } else {
                                    LogUtil.d(TAG, "channelRead::proxy->send prxoy error")
                                    mIChannelChange?.channelStateChange(
                                        mSimpleProxy,
                                        connectProxyState = false,
                                        connectTargetState = false,
                                        errorCode = Error.PROXY_CONNECT_INFO_NONE
                                    )
                                }
                            } else if (data[1].toInt() == Constants.PROXY_SOCKS_AUTH) {
                                LogUtil.d(TAG, "channelRead::PROXY_SOCKS_AUTH")
                                mProxyRequest = Constants.PROXY_REQUEST_AUTH_LOGIN
                                if (TextUtils.isEmpty(mAuthName) || TextUtils.isEmpty(mAuthPassword)) {
                                    LogUtil.d(TAG, "channelRead::auth name or pwd is null")
                                    mINotifyClientCoreConnectState?.notifyClientCoreProxyAuthError()
                                    mIChannelChange?.channelStateChange(
                                        mSimpleProxy,
                                        connectProxyState = false,
                                        connectTargetState = false,
                                        errorCode = Error.AUTH_NAMEPASSWORD_INVALID
                                    )
                                } else {
                                    val authInfo: ByteArray? = Socks5Utils.getInstance()
                                        .buildProxyAuthInfo(mAuthName, mAuthPassword)
                                    if (authInfo != null) {
                                        val bufAuthInfo = Unpooled.buffer(authInfo.size)
                                        bufAuthInfo.writeBytes(authInfo)
                                        ctx?.writeAndFlush(bufAuthInfo)
                                    } else {
                                        LogUtil.d(TAG, "channelRead::proxy->send auth error")
                                        mIChannelChange?.channelStateChange(
                                            mSimpleProxy,
                                            connectProxyState = true,
                                            connectTargetState = false,
                                            errorCode = Error.PROXY_AUTH_INFO_NONE
                                        )
                                    }
                                }
                            } else {
                                LogUtil.e(TAG, "channelRead::UNKNOWN_PROXY_AUTH")
                            }
                        }
                    }
                    Constants.PROXY_REQUEST_AUTH_LOGIN -> {
                        LogUtil.d(TAG, "channelRead::PROXY_REQUEST_AUTH_LOGIN")
                        if (data.size == 2 && data[1].toInt() == Constants.PROXY_AUTH_SUCCESS) {
                            LogUtil.d(TAG, "channelRead::PROXY_REQUEST_AUTH_LOGIN do login")
                            mProxyRequest = Constants.PROXY_REQUEST_CONNECT_TARGET_HOST
                            val hostIP = getHostIP(mTargetIP)
                            val proxySendConnectInfo: ByteArray? = Socks5Utils.getInstance()
                                .buildProxySendConnectInfo(hostIP, mTargetPort.toByte())
                            if (proxySendConnectInfo != null) {
                                val bufHead = Unpooled.buffer(proxySendConnectInfo.size)
                                bufHead.writeBytes(proxySendConnectInfo)
                                ctx!!.writeAndFlush(bufHead)
                            } else {
                                LogUtil.d(TAG, "channelRead::proxy->do login send prxoy error")
                                mIChannelChange?.channelStateChange(
                                    mSimpleProxy,
                                    connectProxyState = true,
                                    connectTargetState = false,
                                    errorCode = Error.PROXY_CONNECT_INFO_NONE
                                )
                            }
                        } else {
                            LogUtil.d(TAG, "channelRead::proxy->auth fail")
                            mINotifyClientCoreConnectState?.notifyClientCoreProxyAuthError()
                            mIChannelChange?.channelStateChange(
                                mSimpleProxy,
                                connectProxyState = true,
                                connectTargetState = false,
                                errorCode = Error.PROXY_AUTH_FAIL
                            )
                        }
                    }
                    Constants.PROXY_REQUEST_CONNECT_TARGET_HOST -> {
                        LogUtil.d(TAG, "channelRead::PROXY_REQUEST_CONNECT_TARGET_HOST")
                        val result = data[1].toInt()
                        if (data[0].toInt() == Constants.PROXY_SOCKS_VERION && result == Constants.PROXY_CONNECT_SUCCESS) {
                            LogUtil.d(TAG, "channelRead::PROXY_CONNECT_SUCCESS")
                            mINotifyClientCoreConnectState?.notifyClientCoreConnectSuccess(ctx?.channel())
                            mINotifyProxyStateChange?.notifyProxyStateChange(false)
                            mIChannelChange?.channelStateChange(
                                mSimpleProxy,
                                connectProxyState = true,
                                connectTargetState = true,
                                errorCode = Error.NO_ERROR
                            )
                        } else {
                            LogUtil.d(TAG, "channelRead::PROXY_CONNECT_FAIL, fail info is: $result")
                            mIChannelChange?.channelStateChange(
                                mSimpleProxy,
                                connectProxyState = true,
                                connectTargetState = false,
                                errorCode = result
                            )
                        }
                    }
                    else -> {
                        LogUtil.e(TAG, "channelRead::UNKNOWN PROXY")
                    }
                }
            } else {
                mHandler?.post {
                    //change to ui thread
                    mIChannelChange?.channelDataChange(data)
                }
            }
        }
        buf.retain()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        mIChannelChange?.channelException(ctx?.channel(), cause)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        if (evt is IdleStateEvent) {
            when {
                evt.state() == IdleState.READER_IDLE -> {
                    LogUtil.d(TAG, "======READER_IDLE======")
                    mIChannelChange?.channelReadIdle()
                }
                evt.state() == IdleState.WRITER_IDLE -> {
                    LogUtil.d(TAG, "======WRITER_IDLE======")
                    mIChannelChange?.channelWriteIdle()
                }
                evt.state() == IdleState.ALL_IDLE -> {
                    LogUtil.d(TAG, "======ALL_IDLE======")
                    mIChannelChange?.channelAllIdle()
                }
            }
        }
    }

    interface IChannelChange {
        /**
         * @param openProxy whether open proxy function
         * @param connectProxyState if open proxy function, need check proxy connect state first, or the state always false
         * @param connectTargetState whether connect the target server
         * @param errorCode code in cn.jesson.nettyclient.utils.Error
         */
        fun channelStateChange(
            openProxy: Boolean?,
            connectProxyState: Boolean,
            connectTargetState: Boolean,
            errorCode: Int
        )

        fun channelDataChange(msg: ByteArray?)
        fun channelException(channel: Channel?, cause: Throwable?)
        fun channelReadIdle()
        fun channelWriteIdle()
        fun channelAllIdle()

    }

    interface INotifyProxyStateChange {
        fun notifyProxyStateChange(state: Boolean) //state: true means proxy connecting, false means proxy connected
    }

    interface INotifyClientCoreConnectState {
        fun notifyClientCoreConnectSuccess(channel: Channel?)
        fun notifyClientCoreProxyAuthError()
    }

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

}