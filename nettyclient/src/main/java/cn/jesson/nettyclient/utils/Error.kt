package cn.jesson.nettyclient.utils

object Error {

    //----------------below is local connect error----------------//
    const val NO_ERROR = 0
    const val CHANNEL_INACTIVE = 11
    const val PROXY_CONNECT_INFO_NONE = 12
    const val PROXY_AUTH_INFO_NONE = 13
    const val AUTH_NAMEPASSWORD_INVALID = 14
    const val PROXY_AUTH_FAIL = 15
    const val CONNECT_RELEASE = 16

    //----------------below is proxy server error----------------//
    const val GENERAL_SOCKS_SERVER_FAILURE = 1
    const val CONNECTION_NOT_ALLOWED_BY_RULESET = 2
    const val NETWORK_UNREACHABLE = 3
    const val HOST_UNREACHABLE = 4
    const val CONNECTION_REFUSED = 5
    const val TTL_EXPIRED = 6
    const val COMMAND_NOT_SUPPORTED = 7
    const val ADDRESS_TYPE_NOT_SUPPORTED = 8

}