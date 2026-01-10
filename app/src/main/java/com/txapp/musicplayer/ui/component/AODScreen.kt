package com.txapp.musicplayer.ui.component

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.txapp.musicplayer.R
import com.txapp.musicplayer.util.TXAFormat
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.txapp.musicplayer.util.TXASystemSettingsHelper

/**
 * AODScreen - Always On Display Screen
 * 
 * Hiển thị thông tin thời gian, pin, và media controls khi đang phát nhạc
 * Màu sắc được giảm độ sáng để tiết kiệm pin và không làm chói mắt ban đêm
 */
@Composable
fun AODScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    // Media playback params
    isPlayingMusic: Boolean = false,
    nowPlayingTitle: String = "",
    nowPlayingArtist: String = "",
    albumId: Long = -1L,
    progress: Float = 0f,
    position: Long = 0L,
    duration: Long = 0L,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {}
) {
    val showDate by com.txapp.musicplayer.util.TXAAODSettings.showDate.collectAsState()
    val dateFormat by com.txapp.musicplayer.util.TXAAODSettings.dateFormat.collectAsState()
    val showBattery by com.txapp.musicplayer.util.TXAAODSettings.showBattery.collectAsState()
    val clockColor = com.txapp.musicplayer.util.TXAAODSettings.getClockColorCompose()
    val isNightMode by com.txapp.musicplayer.util.TXAAODSettings.nightMode.collectAsState()
    val aodOpacity by com.txapp.musicplayer.util.TXAAODSettings.opacity.collectAsState()
    val dimmedClockColor = remember(clockColor, isNightMode) {
        val baseAlpha = if (isNightMode) 0.6f else 1f
        clockColor.copy(alpha = baseAlpha)
    }
    
    val breathingAnimation by com.txapp.musicplayer.util.TXAAODSettings.breathingAnimation.collectAsState()
    val pixelShiftInterval by com.txapp.musicplayer.util.TXAAODSettings.pixelShiftInterval.collectAsState()
    val isAutoBrightnessEnabled by com.txapp.musicplayer.util.TXAPreferences.aodAutoBrightness.collectAsState()

    val digitalFont = FontFamily(Font(R.font.digital_7))
    
    // Pixel shift
    val shiftHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var shiftX by remember { mutableFloatStateOf(0f) }
    var shiftY by remember { mutableFloatStateOf(0f) }
    
    val shiftRunnable = remember {
        object : Runnable {
            override fun run() {
                shiftX = (Math.random() * 2 - 1).toFloat() * 50f
                shiftY = (Math.random() * 2 - 1).toFloat() * 50f
                shiftHandler.postDelayed(this, pixelShiftInterval)
            }
        }
    }
    
    DisposableEffect(Unit) {
        shiftHandler.postDelayed(shiftRunnable, pixelShiftInterval)
        onDispose { shiftHandler.removeCallbacks(shiftRunnable) }
    }
    
    val animatedShiftX by animateFloatAsState(
        targetValue = shiftX,
        animationSpec = tween(2000),
        label = "shiftX"
    )
    val animatedShiftY by animateFloatAsState(
        targetValue = shiftY,
        animationSpec = tween(2000),
        label = "shiftY"
    )

    // Breathing animation - Giảm độ sáng cho ban đêm
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val currentAlpha = if (breathingAnimation) breathingAlpha * aodOpacity else aodOpacity

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clickable { onDismiss() },
        color = Color.Black
    ) {
        val context = LocalContext.current
        
        // Leveraging WRITE_SETTINGS for Screen Brightness optimization
        DisposableEffect(isNightMode, isAutoBrightnessEnabled) {
            val originalBrightness = try {
                android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Exception) { 100 }
            
            if (isAutoBrightnessEnabled && TXASystemSettingsHelper.canWriteSettings(context)) {
                // Dim screen significantly for AOD
                TXASystemSettingsHelper.setBrightness(context, if (isNightMode) 5 else 20)
            }
            
            onDispose {
                if (isAutoBrightnessEnabled && TXASystemSettingsHelper.canWriteSettings(context)) {
                    // Restore brightness
                    TXASystemSettingsHelper.setBrightness(context, originalBrightness)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(shiftX.dp, shiftY.dp)
                .alpha(currentAlpha),
            contentAlignment = Alignment.Center
        ) {
            ClockDisplay(
                modifier = Modifier.alpha(breathingAlpha),
                showDate = showDate,
                dateFormat = dateFormat,
                showBattery = showBattery,
                clockColor = dimmedClockColor, // Sử dụng màu tối hơn
                digitalFont = digitalFont
            )
        
        // Media Infomation & Controls - Chỉ hiển thị khi đang phát nhạc và cài đặt cho phép
        val showMusic by com.txapp.musicplayer.util.TXAAODSettings.showMusic.collectAsState()
        val showControls by com.txapp.musicplayer.util.TXAAODSettings.showControls.collectAsState()
        
        if (isPlayingMusic && nowPlayingTitle.isNotEmpty() && showMusic) {
            AODMediaControls(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
                    .alpha(breathingAlpha * 0.9f), // Giảm độ sáng thêm
                title = nowPlayingTitle,
                artist = nowPlayingArtist,
                albumId = albumId,
                progress = progress,
                position = position,
                duration = duration,
                isPlaying = isPlayingMusic,
                accentColor = dimmedClockColor,
                showControls = showControls,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious
            )
        }
        
        // Hint to unlock
        Text(
             text = stringResource(R.string.txamusic_aod_tap_unlock),
             color = Color.White.copy(alpha = 0.12f), // Giảm từ 0.2f xuống 0.12f
             fontSize = 12.sp,
             modifier = Modifier
                 .align(Alignment.BottomCenter)
                 .padding(bottom = 32.dp)
        )
    }
}
}

/**
 * Media Controls cho AOD - Album art + progress + controls
 * Màu sắc được tối hoá để không làm sáng màn hình
 */
@Composable
private fun AODMediaControls(
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    albumId: Long,
    progress: Float,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    accentColor: Color,
    showControls: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val context = LocalContext.current
    
    // Alpha tối hơn cho album art
    val albumArtAlpha = 0.35f // Rất tối để không sáng màn hình
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Album Art - Tối hoá
        Surface(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = Color.Black
        ) {
            val albumUri = if (albumId != -1L) {
                android.content.ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
            } else null
            
            Box(modifier = Modifier.alpha(albumArtAlpha)) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(albumUri)
                        .size(200)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val imageState = painter.state
                    if (imageState is coil.compose.AsyncImagePainter.State.Loading || 
                        imageState is coil.compose.AsyncImagePainter.State.Error) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Title & Artist - Màu tối
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.5f), // Giảm độ sáng
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = artist,
            color = Color.White.copy(alpha = 0.3f), // Rất tối
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Progress Bar - Màu tối
        LinearProgressIndicator(
            progress = { progress / 1000f },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp)),
            color = accentColor.copy(alpha = 0.5f),
            trackColor = Color.White.copy(alpha = 0.1f)
        )
        
        // Time
        Row(
            modifier = Modifier.fillMaxWidth(0.7f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = TXAFormat.formatDurationHuman(position),
                color = Color.White.copy(alpha = 0.25f),
                fontSize = 10.sp
            )
            Text(
                text = TXAFormat.formatDurationHuman(duration),
                color = Color.White.copy(alpha = 0.25f),
                fontSize = 10.sp
            )
        }
        
        // Controls - Màu tối
        if (showControls) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Surface(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.3f) // Tối hoá nút play
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockDisplay(
    modifier: Modifier = Modifier,
    showDate: Boolean,
    dateFormat: Int,
    showBattery: Boolean,
    clockColor: Color,
    digitalFont: FontFamily
) {
    var currentTime by remember { mutableStateOf("") }
    var currentSeconds by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableIntStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(dateFormat) {
        while (true) {
            val now = java.util.Calendar.getInstance()
            currentTime = "${TXAFormat.format2Digits(now.get(java.util.Calendar.HOUR_OF_DAY))}:${TXAFormat.format2Digits(now.get(java.util.Calendar.MINUTE))}"
            currentSeconds = TXAFormat.format2Digits(now.get(java.util.Calendar.SECOND))
            currentDate = getFormattedDate(dateFormat)

            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showBattery) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.alpha(0.9f)
            ) {
                Icon(
                    imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                    contentDescription = null,
                    tint = clockColor, // Dùng màu giống đồng hồ
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = "$batteryLevel%",
                    color = clockColor, // Dùng màu giống đồng hồ
                    fontSize = 72.dp.value.sp, // Dùng dp value để có kích thước cố định
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "$currentTime:$currentSeconds",
            color = clockColor,
            fontSize = 96.dp.value.sp, // Dùng dp value để kích thước cố định trên mọi device
            fontFamily = digitalFont,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (showDate) {
            Surface(
                color = clockColor.copy(alpha = 0.05f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = currentDate.uppercase(),
                    color = clockColor.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Sleep Timer Status
        var remainingTime by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
             while(true) {
                 remainingTime = com.txapp.musicplayer.ui.component.TXASleepTimerManager.getRemainingTime(context)
                 delay(1000L) // Always update every second for real-time display
             }
        }
        
        if (remainingTime > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                 Icon(
                    imageVector = Icons.Outlined.Bedtime, 
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = TXAFormat.formatDuration(remainingTime),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp, // Increased size
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun getFormattedDate(format: Int): String {
    val locale = Locale.getDefault()
    val pattern = when (format) {
        0 -> if (locale.language == "vi") "EEEE, d MMMM, yyyy" else "EEEE, MMMM d, yyyy"
        1 -> if (locale.language == "vi") "EEE, d MMMM" else "EEE, MMMM d"
        2 -> if (locale.language == "vi") "d MMMM" else "MMMM d"
        3 -> "dd/MM/yyyy"
        4 -> "MM/dd/yyyy"
        5 -> "dd/MM/yy"
        else -> if (locale.language == "vi") "d MMMM" else "MMMM d"
    }
    var result = SimpleDateFormat(pattern, locale).format(Date())
    if (locale.language == "vi") {
         result = result.replace("Thứ Hai", "T2")
                       .replace("Thứ Ba", "T3")
                       .replace("Thứ Tư", "T4")
                       .replace("Thứ Năm", "T5")
                       .replace("Thứ Sáu", "T6")
                       .replace("Thứ Bảy", "T7")
                       .replace("Chủ Nhật", "CN")
                       .replace("Th 2", "T2")
                       .replace("Th 3", "T3")
                       .replace("Th 4", "T4")
                       .replace("Th 5", "T5")
                       .replace("Th 6", "T6")
                       .replace("Th 7", "T7")
                       .replace("CN", "CN")
    }
    return result
}
