package cn.jesson.nettyclient.notification

import android.annotation.TargetApi
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import cn.jesson.nettyclient.utils.Constants

class ServiceNotificationUtils private constructor() {
    private var mContext: Application? = null
    private var manager: NotificationManager? = null
        get() {
            if (field == null) {
                field =
                    mContext!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            }
            return field
        }

    var mKeepLive: Boolean = false
    var mTitle: String = "foreground server"
    var mContent: String = "we are running"
    var mSmallIcon: Int? = null

    companion object {
        private const val TAG = "ServiceNotificationUtils"
        private const val id = "foreground_channel"
        private const val name = "foreground_server"
        private var notificationUtils: ServiceNotificationUtils? = null
        val instance: ServiceNotificationUtils?
            get() {
                if (notificationUtils == null) {
                    synchronized(ServiceNotificationUtils::class.java) {
                        if (notificationUtils == null) {
                            notificationUtils =
                                ServiceNotificationUtils()
                        }
                    }
                }
                return notificationUtils
            }
    }

    fun initContext(context: Application) {
        this.mContext = context
        if (mSmallIcon == null) {
            mSmallIcon = mContext?.applicationInfo?.icon
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
        channel.setSound(null, null)
        manager?.createNotificationChannel(channel)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun getChannelNotification(): Notification.Builder {
        return Notification.Builder(mContext, id)
            .setContentTitle(mTitle)
            .setContentText(mContent)
            .setSmallIcon(mSmallIcon!!)
    }

    @Suppress("DEPRECATION")
    private fun getNotification_25(): NotificationCompat.Builder {
        return NotificationCompat.Builder(mContext)
            .setContentTitle(mTitle)
            .setContentText(mContent)
            .setSmallIcon(mSmallIcon!!)
            .setOngoing(mKeepLive)
    }

    fun getNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getChannelNotification().build()
        } else {
            getNotification_25().build()
        }
    }

    fun sendNotification(notification: Notification?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }else{
            if(!mKeepLive){
                return
            }
        }
        manager?.notify(Constants.FOREGROUND_SERVER_NOTIFY_ID, notification)
    }

    fun clearAllNotify() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            }
            manager?.cancelAll()
        } catch (r: Exception) {
            r.printStackTrace()
        }
    }

}