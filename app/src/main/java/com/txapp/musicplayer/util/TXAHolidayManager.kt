package com.txapp.musicplayer.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import com.txapp.musicplayer.ui.HolidayDialogFragment
import java.util.Calendar

/**
 * Advanced Holiday Manager for TXA Music.
 * - Manages Holiday Greetings (New Year, Tet).
 * - Logic for Dialog restricted to Android 15+ (API 35+) and once per day.
 * - Handles holiday border effects and notifications for all versions.
 */
object TXAHolidayManager {

    private const val PREF_NAME = "txa_holiday_prefs"
    private const val KEY_LAST_SHOWN_DATE = "last_shown_date"
    private const val KEY_LAST_PLAYED_TET_MUSIC = "last_played_tet_music"
    private const val MIN_API_LEVEL = 35 

    enum class HolidayEventType {
        NEW_YEAR_SOLAR,
        TET_27, TET_28, TET_29, TET_30,
        GIAO_THUA,
        TET_MUNG_1,
        TET_MUNG_1_EXTRA,
        TET_MUNG_2, TET_MUNG_3, TET_MUNG_4
    }

    enum class HolidayMode {
        NONE,
        NORMAL, // Mùng 1, Solar New Year
        TAT_NIEN // 27, 28, 29, 30 (cuối năm)
    }

    /**
     * Entry point from MainActivity. Shows the greeting dialog if applicable.
     * Restricted to Android 15+ and once per day.
     */
    fun checkAndShowHoliday(activity: AppCompatActivity) {
        TXALogger.holidayD("TXAHoliday", "Checking holiday events... SDK: ${Build.VERSION.SDK_INT}")
        
        // 1. Auto-play Tet Music (Lunar New Year Day 1, 2, 3) - All Versions
        checkAndPlayTetMusic(activity)

        // 2. Enforce Android 15+ (API 35) for GREETING DIALOG
        if (Build.VERSION.SDK_INT < MIN_API_LEVEL) {
             TXALogger.holidayD("TXAHoliday", "Skipping Greeting Dialog: SDK tool old (< 35)")
             return
        }

        // 3. Date Check (Once per day)
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        val year = calendar.get(Calendar.YEAR)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        val todayKey = "$year-$month-$day"
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastShown = prefs.getString(KEY_LAST_SHOWN_DATE, "")
        
        if (lastShown == todayKey) {
            TXALogger.holidayD("TXAHoliday", "Dialog already shown today: $lastShown")
            return 
        }

        // 3. Holiday Identification
        val events = getActiveEvents(day, month, year, hour)
        val eventType = events.firstOrNull() // Just show first event as a dialog
        
        // 4. Show Dialog
        if (eventType != null) {
            TXALogger.holidayI("TXAHoliday", "Showing greeting dialog for $eventType")
            try {
                showDialog(activity, eventType)
                prefs.edit().putString(KEY_LAST_SHOWN_DATE, todayKey).apply()
            } catch (e: Exception) {
                TXALogger.holidayE("TXAHoliday", "Error showing dialog: ${e.message}", e)
            }
        }
    }

    /**
     * Automatically plays random music from assets/mp3/tet on the first 3 days of Tet.
     * Only once per day.
     */
    private fun checkAndPlayTetMusic(context: Context) {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        
        val lunar = LunarCalendar.getStrictTetRangeDate(day, month, year) ?: return
        
        // Only First 3 days of Tet (Mùng 1, 2, 3)
        if (lunar.month != 1 || lunar.day > 3) return
        
        val todayKey = "TET_${lunar.year}_${lunar.month}_${lunar.day}"
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastPlayed = prefs.getString(KEY_LAST_PLAYED_TET_MUSIC, "")
        
        if (lastPlayed == todayKey) {
            TXALogger.holidayD("TXAHoliday", "Tet music already played today: $todayKey")
            return
        }

        try {
            val assetManager = context.assets
            val tetFiles = assetManager.list("mp3/tet")?.filter { it.endsWith(".mp3") } ?: emptyList()
            
            if (tetFiles.isNotEmpty()) {
                val selectedFile = tetFiles.random()
                val assetUri = "asset:///mp3/tet/$selectedFile"
                
                TXALogger.holidayI("TXAHoliday", "Auto-playing Tet music: $assetUri")
                
                val intent = Intent(context, com.txapp.musicplayer.service.MusicService::class.java).apply {
                    action = com.txapp.musicplayer.service.MusicService.ACTION_PLAY_EXTERNAL_URI
                    putExtra(com.txapp.musicplayer.service.MusicService.EXTRA_URI, assetUri)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                
                // Mark as played
                prefs.edit().putString(KEY_LAST_PLAYED_TET_MUSIC, todayKey).apply()
                
                // Set current layout to "Tết" mode in UX if possible? 
                // For now just playing is enough per request.
            }
        } catch (e: Exception) {
            TXALogger.holidayE("TXAHoliday", "Failed to play Tet music: ${e.message}")
        }
    }

    /**
     * Returns active holiday events for a specific time.
     */
    private fun getActiveEvents(day: Int, month: Int, year: Int, hour: Int): List<HolidayEventType> {
        val list = mutableListOf<HolidayEventType>()
        
        // 1. Solar New Year
        if (day == 1 && month == 1) {
            list.add(HolidayEventType.NEW_YEAR_SOLAR)
        }
        
        // 2. Lunar New Year
        // 2. Lunar New Year
        val lunar = LunarCalendar.getStrictTetRangeDate(day, month, year)
        if (lunar != null) {
            if (lunar.month == 1) {
                when (lunar.day) {
                    1 -> {
                        if (hour >= 0) list.add(HolidayEventType.GIAO_THUA)
                        if (hour >= 7) {
                            list.add(HolidayEventType.TET_MUNG_1)
                            list.add(HolidayEventType.TET_MUNG_1_EXTRA)
                        }
                    }
                    2 -> list.add(HolidayEventType.TET_MUNG_2)
                    3 -> list.add(HolidayEventType.TET_MUNG_3)
                    4 -> list.add(HolidayEventType.TET_MUNG_4)
                }
            } else if (lunar.month == 12) {
                when (lunar.day) {
                    27 -> list.add(HolidayEventType.TET_27)
                    28 -> list.add(HolidayEventType.TET_28)
                    29 -> list.add(HolidayEventType.TET_29)
                    30 -> list.add(HolidayEventType.TET_30)
                }
            }
        }
        
        return list
    }

    /**
     * Used by HolidayDialogFragment to mark as shown manually (legacy support)
     */
    fun markAsShown(context: Context, type: HolidayEventType) {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val todayKey = "$year-$month-$day"
        
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_SHOWN_DATE, todayKey).apply()
        TXALogger.holidayI("TXAHoliday", "Marked as shown for today: $todayKey")
    }

    /**
     * Determines if the holiday border effect should be drawn.
     */
    /**
     * Determines the active holiday mode for effects.
     * Active between 00:00 and 07:00.
     */
    fun getHolidayMode(): HolidayMode {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        
        // Active between 00:00 and 07:00 on holidays
        if (hour < 0 || hour >= 7) {
            return HolidayMode.NONE
        }
        
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        
        // Check for Lunar Year End (Tet Nien: 27, 28, 29, 30)
        val lunar = LunarCalendar.getStrictTetRangeDate(day, month, year)
        if (lunar != null && lunar.month == 12) {
             if (lunar.day >= 27) {
                 return HolidayMode.TAT_NIEN
             }
        }
        
        // Other holidays (Mung 1, Solar New Year)
        val events = getActiveEvents(day, month, year, hour)
        return if (events.isNotEmpty()) HolidayMode.NORMAL else HolidayMode.NONE
    }

    /**
     * Backward compatibility helper
     */
    fun isHolidayBorderActive(): Boolean {
        return getHolidayMode() != HolidayMode.NONE
    }

    /**
     * Schedules periodic checks and notifications.
     */
    fun scheduleNotifications(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        TXALogger.holidayI("TXAHoliday", "--- Bắt đầu lập lịch thông báo lễ hội ---")
        
        scheduleDailyCheck(context, am)
        
        scheduleDailyCheck(context, am)
        
        TXALogger.holidayI("TXAHoliday", "--- Hoàn tất lập lịch thông báo ---")
    }

    private fun scheduleDailyCheck(context: Context, am: AlarmManager) {
        scheduleAlarm(context, am, 0, 0, 201, "com.txapp.musicplayer.action.SHOW_HOLIDAY_NOTI")
        TXALogger.holidayI("TXAHoliday", "Lịch Check [00:00]: OK")
        
        scheduleAlarm(context, am, 7, 0, 202, "com.txapp.musicplayer.action.SHOW_HOLIDAY_NOTI")
        TXALogger.holidayI("TXAHoliday", "Lịch Check [07:00]: OK")
    }

    private fun scheduleAlarm(context: Context, am: AlarmManager, hour: Int, minute: Int, requestCode: Int, action: String) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        val intent = Intent(context, TXAHolidayNotificationReceiver::class.java).apply {
            this.action = action
            putExtra("hour", hour)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val canExact = am.canScheduleExactAlarms()
                TXALogger.holidayD("TXAHoliday", "[Alarm Permission] canScheduleExactAlarms() = $canExact (Android ${Build.VERSION.SDK_INT})")
                
                if (canExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    TXALogger.holidayI("TXAHoliday", "[Alarm] Scheduled EXACT alarm for $hour:${TXAFormat.format2Digits(minute)} (Request: $requestCode)")
                } else {
                    // Fallback to inexact alarm when permission not granted
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    TXALogger.holidayW("TXAHoliday", "[Alarm] Permission denied! Using INEXACT alarm for $hour:${TXAFormat.format2Digits(minute)} (Request: $requestCode). Grant 'Alarms & Reminders' permission for better accuracy.")
                }
            } else {
                // Pre-Android 12: Exact alarms are allowed by default
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                TXALogger.holidayI("TXAHoliday", "[Alarm] Scheduled EXACT alarm for $hour:${TXAFormat.format2Digits(minute)} (Request: $requestCode, Pre-S API)")
            }
        } catch (e: Exception) {
            TXALogger.holidayE("TXAHoliday", "Failed to schedule alarm: ${e.message}")
        }
    }

    private fun showDialog(activity: AppCompatActivity, type: HolidayEventType) {
        val dialog = HolidayDialogFragment.newInstance(type)
        dialog.show(activity.supportFragmentManager, "HolidayDialog_${type.name}")
    }
}
