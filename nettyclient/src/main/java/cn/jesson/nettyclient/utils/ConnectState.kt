package cn.jesson.nettyclient.utils

object ConnectState {

    //----------------below is local connect error----------------//
    const val CONNECTING = 0
    const val CONNECTED = 11 //This state only indicates that the TCP connection is successful, and does not mean that the protocol interaction with the server is successful
    const val ERROR_CHANNEL_INACTIVE = 12
    const val ERROR_PROXY_CONNECT_INFO_NONE = 13
    const val ERROR_PROXY_AUTH_INFO_NONE = 14
    const val ERROR_AUTH_NAMEPASSWORD_INVALID = 15
    const val ERROR_PROXY_AUTH_FAIL = 16
    const val CONNECT_RELEASE = 17

    //----------------below is proxy server error----------------//
    const val ERROR_GENERAL_SOCKS_SERVER_FAILURE = 1
    const val ERROR_CONNECTION_NOT_ALLOWED_BY_RULESET = 2
    const val ERROR_NETWORK_UNREACHABLE = 3
    const val ERROR_HOST_UNREACHABLE = 4
    const val ERROR_CONNECTION_REFUSED = 5
    const val ERROR_TTL_EXPIRED = 6
    const val ERROR_COMMAND_NOT_SUPPORTED = 7
    const val ERROR_ADDRESS_TYPE_NOT_SUPPORTED = 8

}