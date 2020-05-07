package jesson.com.nettyclinet.utils

object Constants {
    const val PROXY_SOCKS_VERION = 0x05

    const val PROXY_SOCKS_RES_BY_TCP = 0x01 //UDP:0x03

    const val PROXY_SOCKS_ATYP_IPV4 = 0x01 //IPV6:0x03, DOMAIN:0x04

    const val PROXY_SOCKS_AUTH_VERSION = 0x01 //auth ver
    const val PROXY_SOCKS_AUTH_NONE = 0x00 //no auth
    const val PROXY_SOCKS_AUTH_NAMEPASSWORD = 0x02 //use name and password auth


    const val PROXY_REQUEST_NONE = 1
    const val PROXY_REQUEST_INIT = 2
    const val PROXY_REQUEST_CONNECT_TARGET_HOST = 3
    const val PROXY_REQUEST_AUTH_LOGIN = 4

    const val PROXY_CONNECT_SUCCESS = 0x00
    const val PROXY_AUTH_SUCCESS = 0x00
    const val PROXY_AUTH_FAIL = 0x01

}