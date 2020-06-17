package cn.jesson.nettyclient.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo

class NetworkUtils {

    companion object {
        fun isConnected(context: Context?): Boolean {
            val info: NetworkInfo? = getActiveNetworkInfo(context)
            return info != null && info.isConnected
        }

        /**
         * need <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`
         * @return NetworkInfo
         */
        @SuppressLint("MissingPermission")
        private fun getActiveNetworkInfo(context: Context?): NetworkInfo? {
            val manager =
                context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return manager.activeNetworkInfo
        }
    }


}