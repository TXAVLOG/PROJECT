package com.txapp.musicplayer.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.txapp.musicplayer.util.TXAPreferences
import com.txapp.musicplayer.util.txa

/**
 * Playback Speed Dialog - Cho phép điều chỉnh tốc độ phát nhạc
 * Đồng bộ real-time với TXAPreferences.playbackSpeed
 */
@Composable
fun PlaybackSpeedDialog(
    onDismiss: () -> Unit,
    onSpeedChanged: (Float) -> Unit
) {
    // Đồng bộ với TXAPreferences
    val currentSpeed by TXAPreferences.playbackSpeed.collectAsState()
    var sliderValue by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    
    // Preset speeds
    val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    
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
                            Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "txamusic_playback_speed".txa(),
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
                
                // Speed display
                Text(
                    text = String.format("%.2fx", sliderValue),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = when {
                        sliderValue < 0.8f -> "txamusic_speed_slower".txa()
                        sliderValue > 1.2f -> "txamusic_speed_faster".txa()
                        else -> "txamusic_speed_normal".txa()
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Slider
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0.5f..3.0f,
                    steps = 24, // (3.0 - 0.5) / 0.1 = 25 values, so 24 steps
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Preset buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    presets.forEach { speed ->
                        FilterChip(
                            selected = kotlin.math.abs(sliderValue - speed) < 0.01f,
                            onClick = { sliderValue = speed },
                            label = { 
                                Text(
                                    text = if (speed == 1.0f) "1x" else "${speed}x",
                                    fontSize = 12.sp
                                ) 
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Reset button
                    OutlinedButton(
                        onClick = { sliderValue = 1.0f },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("txamusic_btn_reset".txa())
                    }
                    
                    // Apply button
                    Button(
                        onClick = {
                            // Lưu vào Preferences (sẽ tự động đồng bộ với Settings)
                            TXAPreferences.currentPlaybackSpeed = sliderValue
                            onSpeedChanged(sliderValue)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("txamusic_btn_apply".txa())
                    }
                }
            }
        }
    }
}
