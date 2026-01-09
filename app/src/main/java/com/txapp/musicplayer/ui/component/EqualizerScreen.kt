package com.txapp.musicplayer.ui.component

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.txapp.musicplayer.service.MusicService
import com.txapp.musicplayer.util.TXAEqualizerManager
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa

/**
 * Custom Equalizer Screen with Compose UI
 * Features:
 * - 5-band equalizer with vertical sliders
 * - Preset selector
 * - Bass Boost control
 * - Virtualizer (3D) control
 * - Enable/Disable toggle
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit
) {
    // Periodically check for audio session ID if not available
    LaunchedEffect(Unit) {
        while (true) {
            val sessionId = MusicService.audioSessionId
            if (sessionId > 0) {
                TXAEqualizerManager.init(sessionId)
                break // Success, stop retrying
            }
            kotlinx.coroutines.delay(1000) // Retry every second
        }
    }
    
    val isEnabled by TXAEqualizerManager.isEnabled.collectAsState()
    val bandLevels by TXAEqualizerManager.bandLevels.collectAsState()
    val bassBoostStrength by TXAEqualizerManager.bassBoostStrength.collectAsState()
    val virtualizerStrength by TXAEqualizerManager.virtualizerStrength.collectAsState()
    val currentPreset by TXAEqualizerManager.currentPreset.collectAsState()
    
    val isAvailable by TXAEqualizerManager.isInitialized.collectAsState()
    val numberOfBands = TXAEqualizerManager.numberOfBands.toInt()
    val bandRange = TXAEqualizerManager.bandLevelRange
    val centerFreqs = TXAEqualizerManager.centerFrequencies
    val presets = TXAEqualizerManager.presetNames
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "txamusic_settings_eq_title".txa(), 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    // Reset button
                    IconButton(
                        onClick = { TXAEqualizerManager.reset() },
                        enabled = isAvailable && isEnabled
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Reset")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        if (!isAvailable) {
            // No equalizer available - show message
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.MusicOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "txamusic_settings_eq_no_session".txa(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "txamusic_settings_eq_play_first".txa(),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enable toggle
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isEnabled) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isEnabled)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Equalizer,
                                    contentDescription = null,
                                    tint = if (isEnabled) 
                                        MaterialTheme.colorScheme.onPrimary 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "txamusic_eq_enable".txa(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = if (isEnabled) "txamusic_eq_on".txa() else "txamusic_eq_off".txa(),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { TXAEqualizerManager.setEnabled(it) }
                            )
                        }
                    }
                }
                
                // Presets
                if (presets.isNotEmpty()) {
                    item {
                        Text(
                            text = "txamusic_eq_presets".txa(),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
                        LazyRow(
                            modifier = Modifier.widthIn(max = screenWidth),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Custom preset
                            item {
                                PresetChip(
                                    name = "txamusic_eq_custom".txa(),
                                    isSelected = currentPreset == -1,
                                    enabled = isEnabled,
                                    onClick = { }
                                )
                            }
                            itemsIndexed(presets) { index, preset ->
                                PresetChip(
                                    name = preset,
                                    isSelected = currentPreset == index,
                                    enabled = isEnabled,
                                    onClick = { TXAEqualizerManager.usePreset(index) }
                                )
                            }
                        }
                    }
                }
                
                // Band Equalizer
                item {
                    Text(
                        text = "txamusic_eq_bands".txa(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Band sliders
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val context = LocalContext.current
                                var hasWarned by remember { mutableStateOf(false) }

                                bandLevels.forEachIndexed { index, level ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Vertical Slider
                                        VerticalSlider(
                                            value = level.toFloat(),
                                            onValueChange = { 
                                                val newValue = it.toInt()
                                                
                                                // Warning logic for ±15dB
                                                if (kotlin.math.abs(newValue) > 1500) {
                                                    if (!hasWarned) {
                                                        com.txapp.musicplayer.util.TXAToast.warning(context, "txamusic_eq_limit_warning".txa())
                                                        hasWarned = true
                                                    }
                                                } else if (kotlin.math.abs(level) > 1500 && kotlin.math.abs(newValue) <= 1500) {
                                                    // Reset warning if they go back under 15dB
                                                    hasWarned = false
                                                }

                                                TXAEqualizerManager.setBandLevel(index, newValue)
                                            },
                                            valueRange = -3000f..3000f, 
                                            steps = 60, // 61 positions total (60 intervals of 100mB/1dB)
                                            enabled = isEnabled,
                                            label = "${if (level > 0) "+" else ""}${level / 100}dB",
                                            modifier = Modifier
                                                .height(130.dp) // standard height
                                                .width(42.dp)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Frequency label
                                        Text(
                                            text = formatFrequency(centerFreqs.getOrNull(index) ?: 0),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Bass Boost
                if (TXAEqualizerManager.isBassBoostSupported()) {
                    item {
                        EffectSliderCard(
                            title = "txamusic_eq_bass_boost".txa(),
                            icon = Icons.Outlined.Speaker,
                            value = bassBoostStrength,
                            onValueChange = { TXAEqualizerManager.setBassBoostStrength(it) },
                            enabled = isEnabled
                        )
                    }
                }
                
                // Virtualizer (3D Effect)
                if (TXAEqualizerManager.isVirtualizerSupported()) {
                    item {
                        EffectSliderCard(
                            title = "txamusic_eq_virtualizer".txa(),
                            icon = Icons.Outlined.SurroundSound,
                            value = virtualizerStrength,
                            onValueChange = { TXAEqualizerManager.setVirtualizerStrength(it) },
                            enabled = isEnabled
                        )
                    }
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun PresetChip(
    name: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled) { onClick() },
        color = if (isSelected) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isSelected) 
                MaterialTheme.colorScheme.onPrimary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectSliderCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..1000f,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    Box(contentAlignment = Alignment.TopCenter) {
                        // Tooltip above thumb
                        Box(
                            modifier = Modifier
                                .offset(y = (-30).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${(value / 10)}%",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Actual Thumb
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.8f))
                            )
                        }
                    }
                }
            )
        }
    }
}

/**
 * Improved Vertical Slider with Precision Tooltip and Markers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Vertical Markers (Background)
        Column(
            modifier = Modifier.height(110.dp).width(26.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drawing 61 lines for 60 intervals
            repeat(61) { i ->
                val dB = 30 - i
                val isMain = dB % 15 == 0
                val isMedium = dB % 5 == 0
                
                if (isMedium) {
                    Box(
                        modifier = Modifier
                            .width(if (isMain) 16.dp else 10.dp)
                            .height(1.dp)
                            .background(
                                if (isMain) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                } else if (i % 2 == 0) { // Every 2dB for small ticks to avoid clutter
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    )
                }
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier
                .rotate(-90f)
                .width(120.dp),
            thumb = {
                Box(contentAlignment = Alignment.Center) {
                    // Tooltip
                    Box(
                        modifier = Modifier
                            .offset(x = 35.dp)
                            .rotate(90f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.9f))
                        )
                    }
                }
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    enabled = enabled,
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp) // refined track
                )
            }
        )
    }
}

private fun formatFrequency(hz: Int): String {
    return when {
        hz >= 1000 -> "${hz / 1000}kHz"
        else -> "${hz}Hz"
    }
}
