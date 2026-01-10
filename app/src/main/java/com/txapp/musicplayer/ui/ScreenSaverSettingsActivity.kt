package com.txapp.musicplayer.ui

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.txapp.musicplayer.R
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.txapp.musicplayer.service.MusicDreamService
import com.txapp.musicplayer.service.TXADreamSettingsHelper
import com.txapp.musicplayer.ui.component.DefaultAlbumArt
import com.txapp.musicplayer.ui.theme.TXAMusicTheme
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXALogger
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.ui.component.TXASleepTimerManager
import androidx.compose.ui.res.stringResource

private val digitalFont = FontFamily(Font(R.font.digital_7))

/**
 * Settings Activity specifically for Screen Saver (DreamService) configuration.
 * This is opened when user taps "Settings" from System Screen Saver picker.
 */
class ScreenSaverSettingsActivity : ComponentActivity() {
    
    private val isDreamSelected = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AOD settings from SharedPreferences
        com.txapp.musicplayer.util.TXAAODSettings.init(this)

        setContent {
            TXAMusicTheme {
                ScreenSaverSettingsScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenSaverSettingsScreen(
    onBack: () -> Unit
) {
    val showControls by com.txapp.musicplayer.util.TXAAODSettings.showControls.collectAsState()
    val nightMode by com.txapp.musicplayer.util.TXAAODSettings.nightMode.collectAsState()
    val showDate by com.txapp.musicplayer.util.TXAAODSettings.showDate.collectAsState()
    val dateFormat by com.txapp.musicplayer.util.TXAAODSettings.dateFormat.collectAsState()
    val showBattery by com.txapp.musicplayer.util.TXAAODSettings.showBattery.collectAsState()
    val clockColor by com.txapp.musicplayer.util.TXAAODSettings.clockColor.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.txamusic_settings_screen_saver),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview Card
            ScreenSaverPreviewCard(
                showControls = showControls,
                nightMode = nightMode,
                showDate = showDate,
                dateFormat = dateFormat,
                showBattery = showBattery,
                clockColorString = clockColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Settings Section Title
            Text(
                text = stringResource(R.string.txamusic_aod_settings_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            // Show Controls Toggle
            SettingsSwitchCard(
                icon = Icons.Outlined.TouchApp,
                title = stringResource(R.string.txamusic_aod_show_controls),
                subtitle = stringResource(R.string.txamusic_aod_show_controls_desc),
                checked = showControls,
                onCheckedChange = { com.txapp.musicplayer.util.TXAAODSettings.setShowControls(context, it) }
            )

            // Night Mode Toggle
            SettingsSwitchCard(
                icon = Icons.Outlined.DarkMode,
                title = stringResource(R.string.txamusic_aod_night_mode),
                subtitle = stringResource(R.string.txamusic_aod_night_mode_desc),
                checked = nightMode,
                onCheckedChange = { com.txapp.musicplayer.util.TXAAODSettings.setNightMode(context, it) }
            )

            // Show Date Toggle
            SettingsSwitchCard(
                icon = Icons.Outlined.CalendarToday,
                title = stringResource(R.string.txamusic_aod_show_date),
                subtitle = "",
                checked = showDate,
                onCheckedChange = { com.txapp.musicplayer.util.TXAAODSettings.setShowDate(context, it) }
            )

            if (showDate) {
                // Date Format Selector
                AODOptionSelector(
                    title = stringResource(R.string.txamusic_aod_date_format),
                    options = listOf(
                        stringResource(R.string.txamusic_aod_date_full),
                        stringResource(R.string.txamusic_aod_date_short),
                        stringResource(R.string.txamusic_aod_date_no_day),
                        "dd/MM/yyyy",
                        "MM/dd/yyyy",
                        "dd/MM/yy"
                    ),
                    selectedIdx = dateFormat,
                    onSelect = { com.txapp.musicplayer.util.TXAAODSettings.setDateFormat(context, it) }
                )
            }

            // Show Battery Toggle
            SettingsSwitchCard(
                icon = Icons.Outlined.BatteryStd,
                title = stringResource(R.string.txamusic_aod_show_battery),
                subtitle = "",
                checked = showBattery,
                onCheckedChange = { com.txapp.musicplayer.util.TXAAODSettings.setShowBattery(context, it) }
            )

            // Clock Color Selector
            AODColorSelector(
                selectedColor = clockColor,
                onColorSelected = { com.txapp.musicplayer.util.TXAAODSettings.setClockColor(context, it) }
            )
            
            // AOD Opacity Slider
            val opacity by com.txapp.musicplayer.util.TXAAODSettings.opacity.collectAsState()
            AODOpacitySlider(
                opacity = opacity,
                onOpacityChange = { com.txapp.musicplayer.util.TXAAODSettings.setOpacity(context, it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Info Card
            InfoCard()

            Spacer(modifier = Modifier.height(8.dp))

            
            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


/**
 * Launch the system dream (screensaver) immediately.
 * Uses the Somnambulator which is the standard Android way to trigger a dream.
 */
private fun startDreamPreview(context: Context) {
    try {
        TXALogger.appI("ScreenSaverSettings", "Launching dream preview via Somnambulator")
        val intent = Intent(Intent.ACTION_MAIN)
        intent.setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        TXALogger.appE("ScreenSaverSettings", "Failed to launch Somnambulator", e)
        // Fallback or Toast? 
        android.widget.Toast.makeText(context, "Could not start preview automatically", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ScreenSaverPreviewCard(
    showControls: Boolean,
    nightMode: Boolean,
    showDate: Boolean,
    dateFormat: Int,
    showBattery: Boolean,
    clockColorString: String
) {
    // Real-time clock state
    var currentTime by remember { mutableStateOf(getCurrentTimeHHmm()) }
    var currentSeconds by remember { mutableStateOf(getCurrentSecondsSS()) }
    var currentDate by remember { mutableStateOf("") }
    
    // Update every second
    LaunchedEffect(dateFormat) {
        while (true) {
            currentTime = getCurrentTimeHHmm()
            currentSeconds = getCurrentSecondsSS()
            currentDate = getPreviewDateFormatted(dateFormat)
            kotlinx.coroutines.delay(1000)
        }
    }
    
    // Breathing alpha animation for clock preview
    val infiniteTransition = rememberInfiniteTransition(label = "preview_breathing")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )

    val baseAlpha = if (nightMode) 0.3f else 1f
    val clockColor = remember(clockColorString) { Color(android.graphics.Color.parseColor(clockColorString)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp), // Increased height for more content
        shape = RoundedCornerShape(20.dp),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(baseAlpha),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                // Battery indicator preview
                if (showBattery) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.alpha(0.6f * breathingAlpha)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryStd,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "85%",
                            color = Color.White,
                            fontSize = 36.sp,
                            fontFamily = digitalFont,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Clock preview - Unified and Big
                Text(
                    text = "$currentTime:$currentSeconds",
                    color = clockColor.copy(alpha = breathingAlpha),
                    fontSize = 72.sp, // Scaled for preview card but feels bigger
                    fontFamily = digitalFont,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                // Capsule Date Preview
                if (showDate) {
                    Surface(
                        color = clockColor.copy(alpha = 0.05f * breathingAlpha),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = currentDate.uppercase(),
                            color = clockColor.copy(alpha = 0.6f * breathingAlpha),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Mock Sleep Timer for preview
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Bedtime, 
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.txamusic_sleep_timer) + " • 15:00",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Music info preview - Artwork
                Box(
                    modifier = Modifier
                        .size(100.dp) // Slightly bigger artwork for preview
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.DarkGray)
                ) {
                    DefaultAlbumArt(vibrantColor = Color.Gray)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.txamusic_aod_preview_song),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.txamusic_aod_preview_artist),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Mock Progress Bar
                LinearProgressIndicator(
                    progress = { 0.4f },
                    modifier = Modifier
                        .width(120.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = Color.White.copy(alpha = 0.6f),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                if (showControls) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Controls preview
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Night mode overlay indicator
            if (nightMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bedtime,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.txamusic_aod_night),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

// Helper functions for time
private fun getCurrentTimeHHmm(): String {
    val now = java.util.Calendar.getInstance()
    val hour = com.txapp.musicplayer.util.TXAFormat.format2Digits(now.get(java.util.Calendar.HOUR_OF_DAY))
    val minute = com.txapp.musicplayer.util.TXAFormat.format2Digits(now.get(java.util.Calendar.MINUTE))
    return "$hour:$minute"
}

private fun getCurrentSecondsSS(): String {
    val now = java.util.Calendar.getInstance()
    return com.txapp.musicplayer.util.TXAFormat.format2Digits(now.get(java.util.Calendar.SECOND))
}

private fun getPreviewDateFormatted(format: Int): String {
    val locale = java.util.Locale.getDefault()
    val pattern = when (format) {
        0 -> if (locale.language == "vi") "EEEE, d MMMM, yyyy" else "EEEE, MMMM d, yyyy" // Full
        1 -> if (locale.language == "vi") "EEE, d MMMM" else "EEE, MMMM d"  // Short
        2 -> if (locale.language == "vi") "d MMMM" else "MMMM d"        // No Day
        3 -> "dd/MM/yyyy"
        4 -> "MM/dd/yyyy"
        5 -> "dd/MM/yy"
        else -> if (locale.language == "vi") "d MMMM" else "MMMM d"
    }
    var result = java.text.SimpleDateFormat(pattern, locale).format(java.util.Date())
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

@Composable
private fun AODOptionSelector(
    title: String,
    options: List<String>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIdx
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(index) },
                    color = if (isSelected) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = option,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AODColorSelector(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        "#FFFFFF", // White
        "#FFD700", // Gold/Amber
        "#00E676", // Green
        "#2196F3", // Blue
        "#FF4081", // Pink
        "#AA00FF", // Purple
        "#FF3D00"  // Orange-Red
    )

    var showCustomColorDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            text = stringResource(R.string.txamusic_aod_clock_color),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colors.forEach { colorHex ->
                val isSelected = colorHex.lowercase() == selectedColor.lowercase()
                val color = Color(android.graphics.Color.parseColor(colorHex))
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onColorSelected(colorHex) }
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (colorHex == "#FFFFFF") Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // Custom Color Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(Color.Red, Color.Green, Color.Blue, Color.Red)))
                    .clickable { showCustomColorDialog = true }
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                 Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Custom",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    
    if (showCustomColorDialog) {
        CustomColorDialog(
            initialColor = selectedColor,
            onDismiss = { showCustomColorDialog = false },
            onColorSelected = { 
                onColorSelected(it)
                showCustomColorDialog = false
            }
        )
    }
}

@Composable
fun CustomColorDialog(
    initialColor: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    // Parse initial color
    val initialInt = try { android.graphics.Color.parseColor(initialColor) } catch (e: Exception) { android.graphics.Color.WHITE }
    var red by remember { mutableFloatStateOf(android.graphics.Color.red(initialInt) / 255f) }
    var green by remember { mutableFloatStateOf(android.graphics.Color.green(initialInt) / 255f) }
    var blue by remember { mutableFloatStateOf(android.graphics.Color.blue(initialInt) / 255f) }
    
    val currentColor = Color(red, green, blue)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.txamusic_aod_color_custom)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Preview
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f), CircleShape)
                )
                
                // Sliders
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Red: ${(red * 255).toInt()}")
                    Slider(
                        value = red, 
                        onValueChange = { red = it },
                        colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
                    )
                    
                    Text("Green: ${(green * 255).toInt()}")
                    Slider(
                        value = green, 
                        onValueChange = { green = it },
                        colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green)
                    )
                    
                    Text("Blue: ${(blue * 255).toInt()}")
                    Slider(
                        value = blue, 
                        onValueChange = { blue = it },
                        colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val r = (red * 255).toInt()
                    val g = (green * 255).toInt()
                    val b = (blue * 255).toInt()
                    val hex = String.format("#%02X%02X%02X", r, g, b)
                    onColorSelected(hex)
                }
            ) {
                Text(stringResource(R.string.txamusic_aod_btn_select))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.txamusic_btn_cancel))
            }
        }
    )
}

@Composable
private fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun InfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.txamusic_aod_info),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun AODOpacitySlider(
    opacity: Float,
    onOpacityChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.txamusic_aod_opacity),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(opacity * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = opacity,
            onValueChange = onOpacityChange,
            valueRange = 0.1f..1.0f,
            steps = 17, // 5% increments
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "10%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "100%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
