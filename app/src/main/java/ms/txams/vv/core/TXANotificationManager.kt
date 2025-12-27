package ms.txams.vv.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import ms.txams.vv.R
import ms.txams.vv.ui.TXAMainActivity
import ms.txams.vv.update.UpdateInfo

/**
 * TXA Notification Manager
 * Handles all app notifications (updates, downloads, etc.)
 */
object TXANotificationManager {

    private const val CHANNEL_ID_UPDATE = "txa_update_channel"
    private const val NOTIFICATION_ID_UPDATE = 1001

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = TXATranslation.txa("txamusic_update_notification_channel_name")
            val descriptionText = TXATranslation.txa("txamusic_update_notification_channel_description")
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID_UPDATE, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showUpdateNotification(context: Context, updateInfo: UpdateInfo) {
        val intent = Intent(context, TXAMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_UPDATE)
            .setSmallIcon(R.drawable.ic_update)
            .setContentTitle(TXATranslation.txa("txamusic_update_available"))
            .setContentText(TXATranslation.txa("txamusic_update_notification_body").format(updateInfo.versionName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_UPDATE, builder.build())
        
        TXABackgroundLogger.i("Notification sent for version ${updateInfo.versionName}")
    }
}
