package cn.jesson.nettyclient.notification

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import cn.jesson.nettyclient.R
import cn.jesson.nettyclient.utils.Constants

class ServiceNotificationUtils private constructor() {
    private var mContext: Context? = null
    private var manager: NotificationManager? = null
        private get() {
            if (field == null) {
                field =
                    mContext!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            }
            return field
        }

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

    fun setContext(context: Context){
        this.mContext = context.applicationContext
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
        channel.setSound(null, null)
        manager?.createNotificationChannel(channel)
    }

    @TargetApi(Build.VERSION_CODES.O)
    fun getChannelNotification(title: String?, content: String?): Notification.Builder {
        return Notification.Builder(mContext, id)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher)
            .setAutoCancel(false)
    }

    fun sendNotification(notification: Notification?) {
        if (Build.VERSION.SDK_INT >= 26) {
            createNotificationChannel()
            manager?.notify(Constants.FOREGROUND_SERVER_NOTIFY_ID, notification)
        }
    }

    fun clearAllNotify() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                createNotificationChannel()
                manager?.cancelAll()
            }
        } catch (r: Exception) {
            r.printStackTrace()
        }
    }

}