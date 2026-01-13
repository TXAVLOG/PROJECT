package com.txapp.musicplayer.appwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txapp.musicplayer.ui.theme.TXAMusicTheme
import com.txapp.musicplayer.util.txa

/**
 * Widget Settings Activity
 * 
 * Uses cached API translations (.txa()) for all UI text.
 * Settings are saved to SharedPreferences and included in Settings backup.
 */
class WidgetSettingsActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            TXAMusicTheme {
                WidgetSettingsScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var settings by remember { mutableStateOf(WidgetSettings.load(context)) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "txamusic_widget_settings".txa(),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "txamusic_btn_back".txa()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Preview Card
            WidgetPreviewCard(settings)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Display Options Section
            Text(
                text = "txamusic_widget_display".txa(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Show Album Art
            SettingSwitch(
                title = "txamusic_widget_show_album_art".txa(),
                checked = settings.showAlbumArt,
                onCheckedChange = { 
                    settings = settings.copy(showAlbumArt = it)
                    settings.save(context)
                }
            )
            
            // Show Title
            SettingSwitch(
                title = "txamusic_widget_show_title".txa(),
                checked = settings.showTitle,
                onCheckedChange = { 
                    settings = settings.copy(showTitle = it)
                    settings.save(context)
                }
            )
            
            // Show Artist
            SettingSwitch(
                title = "txamusic_widget_show_artist".txa(),
                checked = settings.showArtist,
                onCheckedChange = { 
                    settings = settings.copy(showArtist = it)
                    settings.save(context)
                }
            )
            
            // Show Progress
            SettingSwitch(
                title = "txamusic_widget_show_progress".txa(),
                checked = settings.showProgress,
                onCheckedChange = { 
                    settings = settings.copy(showProgress = it)
                    settings.save(context)
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Control Options Section
            Text(
                text = "txamusic_widget_controls".txa(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Show Shuffle
            SettingSwitch(
                title = "txamusic_widget_show_shuffle".txa(),
                checked = settings.showShuffle,
                onCheckedChange = { 
                    settings = settings.copy(showShuffle = it)
                    settings.save(context)
                }
            )
            
            // Show Repeat
            SettingSwitch(
                title = "txamusic_widget_show_repeat".txa(),
                checked = settings.showRepeat,
                onCheckedChange = { 
                    settings = settings.copy(showRepeat = it)
                    settings.save(context)
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Info text
            Text(
                text = "txamusic_widget_info".txa(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WidgetPreviewCard(settings: WidgetSettings) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111111)) // Slightly darker background for contrast
    ) {
        // Shadow/Ambient light effect (optional, keep it simple but premium)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art Preview
            if (settings.showAlbumArt) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✨", // Cosmic vibe like the generated image
                        fontSize = 32.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (settings.showTitle) {
                    Text(
                        text = "Stellar Echoes", // Using example from prompt
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1
                    )
                }
                if (settings.showArtist) {
                    Text(
                        text = "TXA Deep Space",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
                
                if (settings.showProgress) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1:24", color = Color(0xFF888888), fontSize = 10.sp)
                        Text("3:45", color = Color(0xFF888888), fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { 0.4f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF00D269),
                        trackColor = Color(0xFF333333)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Control buttons preview - using icons for better look
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (settings.showShuffle) {
                        Text("🔀", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                    Text("⏮️", fontSize = 18.sp, color = Color.White)
                    
                    // Main Play Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF00D269)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▶️", fontSize = 14.sp)
                    }
                    
                    Text("⏭️", fontSize = 18.sp, color = Color.White)
                    if (settings.showRepeat) {
                        Text("🔁", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00D269)
            )
        )
    }
}
