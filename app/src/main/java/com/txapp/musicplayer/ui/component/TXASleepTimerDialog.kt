package com.txapp.musicplayer.ui.component

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.txapp.musicplayer.service.MusicService
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa
import kotlinx.coroutines.delay

/**
 * Sleep Timer Dialog - Cho phép người dùng đặt hẹn giờ ngủ
 */
@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onSetTimer: (minutes: Int) -> Unit
) {
    val context = LocalContext.current
    
    // State
    var sliderValue by remember { mutableFloatStateOf(15f) }
    val minutes = sliderValue.toInt()
    
    // Check if timer is already running - USE CONTEXT
    var isTimerActive by remember { 
        mutableStateOf(TXASleepTimerManager.isTimerActive(context))
    }
    var remainingTime by remember { mutableLongStateOf(TXASleepTimerManager.getRemainingTime(context)) }
    
    // Refresh timer status when dialog opens and every second
    LaunchedEffect(Unit) {
        while (true) {
            val newRemainingTime = TXASleepTimerManager.getRemainingTime(context)
            val newIsActive = newRemainingTime > 0
            
            remainingTime = newRemainingTime
            isTimerActive = newIsActive
            
            delay(1000)
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "txamusic_sleep_timer".txa(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (isTimerActive) {
                    // Timer đang chạy - hiển thị thời gian còn lại
                    Text(
                        text = "txamusic_sleep_timer_active".txa(),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = TXAFormat.formatDuration(remainingTime),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Cancel button
                    Button(
                        onClick = {
                            TXASleepTimerManager.cancelTimer(context)
                            isTimerActive = false
                            com.txapp.musicplayer.util.TXAToast.info(context, "txamusic_sleep_timer_canceled".txa())
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("txamusic_btn_cancel".txa())
                    }
                } else {
                    // Đặt timer mới
                    Text(
                        text = "txamusic_sleep_timer_desc".txa(),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Time display
                    Text(
                        text = "$minutes " + "txamusic_unit_minutes".txa(),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Slider
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 5f..120f,
                        steps = 22, // 5, 10, 15, ... 120
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    
                    // Quick select buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(15, 30, 45, 60).forEach { time ->
                            FilterChip(
                                selected = minutes == time,
                                onClick = { sliderValue = time.toFloat() },
                                label = { Text("$time" + "txamusic_unit_minute".txa()) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Set timer button
                    Button(
                        onClick = {
                            TXASleepTimerManager.setTimer(context, minutes)
                            isTimerActive = true
                            com.txapp.musicplayer.util.TXAToast.success(
                                context, 
                                "txamusic_sleep_timer_set".txa().replace("%d", minutes.toString())
                            )
                            onSetTimer(minutes)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("txamusic_sleep_timer_start".txa())
                    }
                }
            }
        }
    }
}

/**
 * Sleep Timer Manager - Quản lý hẹn giờ ngủ
 */
object TXASleepTimerManager {
    private const val PREFS_NAME = "txa_sleep_timer"
    private const val KEY_TIMER_END_TIME = "timer_end_time"
    
    fun setTimer(context: Context, minutes: Int) {
        val endTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        
        // Save end time
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TIMER_END_TIME, endTime)
            .apply()
        
        // Set alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_STOP
        }
        val pendingIntent = PendingIntent.getService(
            context, 
            1001, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                endTime,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Fallback for devices without exact alarm permission
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                endTime,
                pendingIntent
            )
        }
    }
    
    fun cancelTimer(context: Context) {
        // Clear saved end time
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TIMER_END_TIME)
            .apply()
        
        // Cancel alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_STOP
        }
        val pendingIntent = PendingIntent.getService(
            context, 
            1001, 
            intent, 
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }
    
    fun isTimerActive(): Boolean {
        return getRemainingTime() > 0
    }
    
    fun getRemainingTime(): Long {
        // We need context, using application context from preferences would be ideal
        // For now, return based on saved time
        return 0L // Will be updated when context is available
    }
    
    fun getRemainingTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endTime = prefs.getLong(KEY_TIMER_END_TIME, 0L)
        val remaining = endTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }
    
    fun isTimerActive(context: Context): Boolean {
        return getRemainingTime(context) > 0
    }
}
