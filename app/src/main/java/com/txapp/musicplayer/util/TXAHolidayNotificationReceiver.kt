package com.txapp.musicplayer.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.txapp.musicplayer.R
import com.txapp.musicplayer.ui.SplashActivity
import java.util.Calendar

class TXAHolidayNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        TXALogger.holidayI("TXAHoliday", "Dòng sự kiện: Đã nhận được intent action = $action")
        
        when (action) {
            "com.txapp.musicplayer.action.SHOW_HOLIDAY_NOTI" -> {
                val hour = intent.getIntExtra("hour", -1)
                processHolidayNotification(context, hour)
            }
            "com.txapp.musicplayer.action.SHOW_HOLIDAY_NOTI_TEST" -> {
                TXALogger.holidayI("TXAHoliday", ">>> Đang gửi thông báo TEST (2:30 AM)...")
                showNotification(context, 999, "TXA Test Notification", "Hệ thống thông báo đang hoạt động tốt, thưa đạo hữu!")
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                TXAHolidayManager.scheduleNotifications(context)
            }
        }
    }

    private fun processHolidayNotification(context: Context, hour: Int) {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        
        val rawLunar = LunarCalendar.getStrictTetRangeDate(day, month, year)
        // If not a Tet holiday, we don't start the Tet logic, but we might still need a 'lunar' object
        // for other checks? The code below checks lunar.month == 1 or lunar.month == 12.
        // So defaulting to dummy (0,0,0) is safe to AVOID Tet logic.
        val lunar = rawLunar ?: LunarCalendar.LunarDate(0, 0, 0)
        
        var title: String? = null
        var body: String? = null
        
        // Custom logic for Giao Thua and Mung 1
        if (lunar.month == 1 && lunar.day == 1) {
            if (hour == 0) {
                // Giao Thua Midnight
                title = "txamusic_holiday_giaothua_title".txa()
                body = "txamusic_holiday_noti_giaothua".txa()
            } else if (hour == 7) {
                // Mung 1 morning
                title = "txamusic_holiday_mung1_title".txa()
                body = "txamusic_holiday_mung1_body".txa()
                
                // For "2 notifications" requirement at 7 AM
                showNotification(context, 778, "txamusic_holiday_mung1_extra_title".txa(), "txamusic_holiday_mung1_extra_body".txa())
            }
        } else if (lunar.month == 1 && lunar.day in 2..4) {
             title = "txamusic_holiday_tet_title".txa()
             body = "txamusic_holiday_noti_body".txa()
        } else if (day == 1 && month == 1) {
             // Solar New Year
             title = "txamusic_holiday_newyear_title".txa()
             body = "txamusic_holiday_newyear_body".txa()
        } else if (lunar.month == 12 && lunar.day in 27..30) {
            // New Year countdown preparations
            when (lunar.day) {
                27 -> {
                    title = "txamusic_holiday_tet_27_title".txa()
                    body = "txamusic_holiday_tet_27_body".txa()
                }
                28 -> {
                    title = "txamusic_holiday_tet_28_title".txa()
                    body = "txamusic_holiday_tet_28_body".txa()
                }
                29 -> {
                    title = "txamusic_holiday_tet_29_title".txa()
                    body = "txamusic_holiday_tet_29_body".txa()
                }
                30 -> {
                    title = "txamusic_holiday_tatnien_title".txa()
                    body = "txamusic_holiday_tatnien_body".txa()
                }
            }
        }

        if (title != null && body != null) {
            val hasGift = body == "txamusic_holiday_noti_body".txa() || body == "txamusic_holiday_newyear_body".txa()
            showNotification(context, 777, title, body, hasGift)
        } else {
            TXALogger.holidayD("TXAHoliday", "Không có sự kiện lễ hội hôm nay (Solar: $day/$month, Lunar: ${lunar.day}/${lunar.month}), bỏ qua thông báo.")
        }
    }

    private fun showNotification(context: Context, id: Int, title: String, body: String, hasGift: Boolean = false) {
        val channelId = "txa_holiday_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "txamusic_holiday_channel_name".txa()
            // High importance to pop up and make sound
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = "Holiday Greetings and Celebrations"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setBypassDnd(true) // Try to bypass DND (requires system permission but good to have)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val splashIntent = Intent(context, com.txapp.musicplayer.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, splashIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
            .setCategory(NotificationCompat.CATEGORY_ALARM) // More likely to bypass DND
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setFullScreenIntent(pendingIntent, true) // High priority pop-up

        // Add Gift Action
        if (hasGift) {
            val giftIntent = Intent(context, com.txapp.musicplayer.ui.MainActivity::class.java).apply {
                action = "com.txapp.musicplayer.action.OPEN_GIFT"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val giftPendingIntent = PendingIntent.getActivity(
                context, 
                id + 100, 
                giftIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Using a simple icon like play or star since we might not have a gift icon
            // Use R.drawable.ic_audiotrack or similar if available, or just a system icon
            // Assuming R.drawable.ic_music_note exists or similar. I'll use 0 or verify.
            // I'll used android.R.drawable.ic_media_play as fallback or just 0 if allowed (usually needs icon)
            // I'll check R file imports or use a safe icon.
            // R.drawable.ic_noti is definitely there.
            builder.addAction(R.drawable.ic_noti, "txamusic_gift_open".txa(), giftPendingIntent)
        }

        val notification = builder.build()

        notificationManager.notify(id, notification)
        vibrateDevice(context)
        TXALogger.holidayI("TXAHoliday", "Thành công: Đã đẩy thông báo [$title] (ID: $id) tới hệ thống.")
    }

    private fun vibrateDevice(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            val pattern = longArrayOf(0, 500, 100, 500, 100, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {}
    }
}
