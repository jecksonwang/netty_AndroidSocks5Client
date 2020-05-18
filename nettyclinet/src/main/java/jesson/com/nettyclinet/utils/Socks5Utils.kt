package jesson.com.nettyclinet.utils

import android.text.TextUtils
import java.lang.ref.WeakReference

class Socks5Utils private constructor() {


    companion object {
        private var instance: Socks5Utils? = null

        @Synchronized
        fun getInstance(): Socks5Utils {
            synchronized(Socks5Utils::class.java) {
                if (instance == null) {
                    instance = Socks5Utils()
                }
            }
            return instance!!
        }
    }

    /**
     * send init request info to proxy server
     */
    fun buildProxyInitInfo(): ByteArray {
        val data = ByteArray(4)
        data[0] = Constants.PROXY_SOCKS_VERION.toByte() //VER socks version 5
        data[1] = 0x02 //auth method num,there are two, one is no auth and other is name and password
        data[2] = Constants.PROXY_SOCKS_AUTH_NONE.toByte() //no auth
        data[3] = Constants.PROXY_SOCKS_AUTH_NAMEPASSWORD.toByte() //username password auth
        return data
    }

    /**
     * auth complete and send target info to proxy server
     */
    fun buildProxySendConnectInfo(targetIp: String?, targetPort: Byte): ByteArray? {
        if (TextUtils.isEmpty(targetIp)) {
            throw IllegalArgumentException("buildProxySendConnectInfo-->targetIp is null,please check")
        }
        val split = targetIp!!.split(".")
        if (split.size == 4) {
            val data = ByteArray(10)
            data[0] = Constants.PROXY_SOCKS_VERION.toByte() //VER socks version 5
            data[1] = Constants.PROXY_SOCKS_RES_BY_TCP.toByte() //CMD 1:tcp, 3:udp
            data[2] = 0x00 //RSV default 0
            data[3] =
                Constants.PROXY_SOCKS_ATYP_IPV4.toByte() //ATYP  1:ip4, 3:domain(string), 4:ip6
            data[4] =
                split[0].toInt().toByte() //DST.ADDR if use 3(domin) this byte is the domain length
            data[5] = split[1].toInt().toByte() //DST.ADDR
            data[6] = split[2].toInt().toByte() //DST.ADDR
            data[7] = split[3].toInt().toByte() //DST.ADDR
            data[8] = (targetPort / 256).toByte() //DST.PORT
            data[9] = (targetPort % 256).toByte() //DST.PORT
            return data
        }
        return null
    }

    /**
     * build auth info to proxy server
     */
    fun buildProxyAuthInfo(name: String?, password: String?): ByteArray? {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(password)) {
            throw IllegalArgumentException("buildProxyAuthInfo-->auth name or auth password is null,please check")
        }
        val data = byteArrayOf(Constants.PROXY_SOCKS_AUTH_VERSION.toByte())
        val nameLen = byteArrayOf(name!!.length.toString().toByte())
        val passwordLen = byteArrayOf(password!!.length.toString().toByte())
        val bname = name.toByteArray()
        val bpassword = password.toByteArray()
        return byteMergerAll(data, nameLen, bname, passwordLen, bpassword)
    }

    private fun byteMergerAll(vararg values: ByteArray): ByteArray? {
        var lengthByte = 0
        for (i in values.indices) {
            lengthByte += values[i].size
        }
        val weakReference =
            WeakReference(ByteArray(lengthByte))
        var countLength = 0
        for (i in values.indices) {
            val b = values[i]
            System.arraycopy(b, 0, weakReference.get(), countLength, b.size)
            countLength += b.size
        }
        return weakReference.get()
    }


}