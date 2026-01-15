package com.txapp.musicplayer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.txapp.musicplayer.ui.component.UpdateDialog
import com.txapp.musicplayer.ui.theme.TXAMusicTheme
import com.txapp.musicplayer.ui.component.TXAIcons
import com.txapp.musicplayer.util.*
import com.txapp.musicplayer.ui.component.EqualizerScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.txapp.musicplayer.ui.component.BlacklistFolderChooserDialog
import com.txapp.musicplayer.data.MusicRepository
import com.txapp.musicplayer.MusicApplication
import com.txapp.musicplayer.data.BlackListEntity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.max

sealed class SettingsRoute {
    object Main : SettingsRoute()
    object Visual : SettingsRoute()
    object Audio : SettingsRoute()
    object Equalizer : SettingsRoute()
    object NowPlaying : SettingsRoute()
    object Personalize : SettingsRoute()
    object Images : SettingsRoute()
    object Other : SettingsRoute()
    object About : SettingsRoute()
    object Backup : SettingsRoute()
    object Power : SettingsRoute()
}

class SettingsActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            TXAMusicTheme {
                var currentRoute by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Main) }
                
                BackHandler(enabled = currentRoute != SettingsRoute.Main) {
                    currentRoute = when (currentRoute) {
                        is SettingsRoute.Equalizer -> SettingsRoute.Audio
                        else -> SettingsRoute.Main
                    }
                }

                AnimatedContent(
                    targetState = currentRoute,
                    transitionSpec = {
                        if (targetState != SettingsRoute.Main) {
                            (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "SettingsNavigation"
                ) { route ->
                    SettingsScreenContent(
                        route = route,
                        onNavigate = { currentRoute = it },
                        onBack = { 
                            if (currentRoute == SettingsRoute.Main) finish() 
                            else currentRoute = SettingsRoute.Main 
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    route: SettingsRoute,
    onNavigate: (SettingsRoute) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Global States
    val languageVersion by TXATranslation.onLanguageChanged.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var availableLanguages by remember { mutableStateOf<List<TXATranslation.LanguageInfo>>(emptyList()) }
    val isDownloadingLanguage by TXATranslation.isDownloadingLanguage.collectAsState()
    var selectedLanguage by remember { mutableStateOf(TXATranslation.getCurrentLocale()) }
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    val updateInfo by TXAUpdateManager.updateInfo.collectAsState()
    val downloadState by TXAUpdateManager.downloadState.collectAsState()
    val isResolving by TXAUpdateManager.isResolving.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showAlbumGridDialog by remember { mutableStateOf(false) }
    var showArtistGridDialog by remember { mutableStateOf(false) }
    var showSocialDialog by remember { mutableStateOf(false) }


    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showBlacklistDialog by remember { mutableStateOf(false) }
    
    val app = context.applicationContext as MusicApplication
    val repository = MusicApplication.instance.repository
    val blacklist by repository.getBlacklistedFolders().collectAsState(initial = emptyList())
    


    LaunchedEffect(Unit, languageVersion) {
        scope.launch {
            availableLanguages = TXATranslation.getAvailableLanguages(context)
            selectedLanguage = TXATranslation.getCurrentLocale()
        }
    }

    Scaffold(
        topBar = {

            if (route !is SettingsRoute.Equalizer) {
                TopAppBar(
                    title = { 
                        val titleRes = when(route) {
                            is SettingsRoute.Main -> "txamusic_settings"
                            is SettingsRoute.Visual -> "txamusic_settings_section_visual"
                            is SettingsRoute.Audio -> "txamusic_settings_section_audio"
                            is SettingsRoute.Equalizer -> "txamusic_settings_eq_title"
                            is SettingsRoute.NowPlaying -> "txamusic_settings_section_now_playing"
                            is SettingsRoute.Personalize -> "txamusic_settings_section_personalize"
                            is SettingsRoute.Images -> "txamusic_settings_section_images"
                            is SettingsRoute.Other -> "txamusic_settings_section_other"
                            is SettingsRoute.About -> "txamusic_settings_section_update_info"
                            is SettingsRoute.Backup -> "txamusic_settings_section_backup"
                            is SettingsRoute.Power -> "txamusic_settings_section_power"
                        }
                        Text(titleRes.txa(), fontWeight = FontWeight.Bold) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (route) {
                is SettingsRoute.Main -> MainSettingsList(onNavigate)
                is SettingsRoute.Visual -> VisualSettings(
                    onLanguageClick = { showLanguageDialog = true },
                    onThemeClick = { showThemeDialog = true },
                    onAccentClick = { showAccentDialog = true },
                    selectedLanguage = selectedLanguage
                )
                is SettingsRoute.Audio -> AudioSettings(
                    onNavigateToEqualizer = { onNavigate(SettingsRoute.Equalizer) }
                )
                is SettingsRoute.Equalizer -> EqualizerScreen(onBack = onBack)
                is SettingsRoute.NowPlaying -> NowPlayingSettings()
                is SettingsRoute.Personalize -> PersonalizeSettings(
                    onGridClick = { showGridDialog = true },
                    onAlbumGridClick = { showAlbumGridDialog = true },
                    onArtistGridClick = { showArtistGridDialog = true },
                    onClearHistoryClick = { showClearHistoryDialog = true },
                    onBlacklistClick = { showBlacklistDialog = true },
                    blacklistedFolders = blacklist
                )
                is SettingsRoute.Images -> ImageSettings()
                is SettingsRoute.Other -> OtherSettings()
                is SettingsRoute.About -> AboutSettings(
                    isChecking = isCheckingUpdate,
                    onCheckUpdate = {
                         scope.launch {
                            isCheckingUpdate = true
                            val info = TXAUpdateManager.checkForUpdate()
                            isCheckingUpdate = false
                            if (info != null) showUpdateDialog = true
                            else TXAToast.info(context, "txamusic_settings_no_update".txa())
                        }
                    },
                    onSocialClick = { showSocialDialog = true }
                )
                is SettingsRoute.Backup -> BackupRestoreSettings()
                is SettingsRoute.Power -> PowerSettings()
            }
        }
    }

    // Dialogs Implementation
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            availableLanguages = availableLanguages,
            currentLocale = selectedLanguage,
            isLoading = isDownloadingLanguage,
            onSelect = { langCode ->
                scope.launch {
                    TXATranslation.downloadAndApply(context, langCode)
                    showLanguageDialog = false
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            downloadState = downloadState,
            resolving = isResolving,
            onUpdateClick = { TXAUpdateManager.startDownload(context, updateInfo!!) },
            onDismiss = { 
                showUpdateDialog = false
                TXAUpdateManager.resetDownloadState()
            },
            onInstallClick = { file -> TXAUpdateManager.installApk(context, file) }
        )
    }

    if (showThemeDialog) {
        SelectionDialog(
            title = "txamusic_settings_theme_title".txa(),
            items = listOf("system", "light", "dark"),
            selectedItem = TXAPreferences.currentTheme,
            itemLabel = { it.replaceFirstChar { it.uppercase() } },
            onSelect = { TXAPreferences.currentTheme = it; showThemeDialog = false },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showAccentDialog) {
        ColorSelectionDialog(
            selectedColor = TXAPreferences.currentAccent,
            onSelect = { TXAPreferences.currentAccent = it; showAccentDialog = false },
            onDismiss = { showAccentDialog = false }
        )
    }

    if (showGridDialog) {
        SelectionDialog(
            title = "txamusic_settings_grid_title".txa(),
            items = listOf(1, 2, 3, 4),
            selectedItem = TXAPreferences.currentGridSize,
            itemLabel = { "$it Column(s)" },
            onSelect = { TXAPreferences.currentGridSize = it; showGridDialog = false },
            onDismiss = { showGridDialog = false }
        )
    }

    if (showAlbumGridDialog) {
        SelectionDialog(
            title = "txamusic_settings_album_grid_size".txa(),
            items = listOf(1, 2, 3, 4),
            selectedItem = TXAPreferences.currentAlbumGridSize,
            itemLabel = { "$it Column(s)" },
            onSelect = { TXAPreferences.currentAlbumGridSize = it; showAlbumGridDialog = false },
            onDismiss = { showAlbumGridDialog = false }
        )
    }

    if (showArtistGridDialog) {
        SelectionDialog(
            title = "txamusic_settings_artist_grid_size".txa(),
            items = listOf(1, 2, 3, 4),
            selectedItem = TXAPreferences.currentArtistGridSize,
            itemLabel = { "$it Column(s)" },
            onSelect = { TXAPreferences.currentArtistGridSize = it; showArtistGridDialog = false },
            onDismiss = { showArtistGridDialog = false }
        )
    }

    if (showSocialDialog) {
        SocialLinksDialog(onDismiss = { showSocialDialog = false })
    }

    // History Management Dialog (View & Clear)
    if (showClearHistoryDialog) {
        val scope = rememberCoroutineScope()
        var historyMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
        
        LaunchedEffect(Unit) {
            historyMap = com.txapp.musicplayer.util.TXAPlaybackHistory.getHistoryMap()
        }

        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("txamusic_history_dialog_title".txa(), style = MaterialTheme.typography.titleLarge)
                }
            },
            text = { 
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "txamusic_clear_history_confirm".txa(), 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // List of saved songs
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        if (historyMap.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No history found", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            items(historyMap.entries.toList()) { (path, pos) ->
                                val fileName = File(path).name
                                val timeString = com.txapp.musicplayer.util.TXAFormat.formatDuration(pos)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = timeString,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            com.txapp.musicplayer.util.TXAPlaybackHistory.clearAll(context)
                            TXAToast.success(context, "txamusic_history_deleted".txa())
                        }
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("txamusic_action_delete".txa()) // "Delete All" ideally
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearHistoryDialog = false }) {
                    Text("txamusic_btn_close".txa())
                }
            }
        )
    }
    if (showBlacklistDialog) {
        BlacklistFolderChooserDialog(
            currentBlacklist = blacklist,
            onDismiss = { showBlacklistDialog = false },
            onAddBlacklist = { path ->
                scope.launch {
                    repository.addToBlacklist(path)
                }
            },
            onRemoveBlacklist = { path ->
                scope.launch {
                    repository.removeFromBlacklist(path)
                }
            }
        )
    }
}

@Composable
fun MainSettingsList(onNavigate: (SettingsRoute) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            CategoryCard(
                icon = Icons.Outlined.Palette,
                iconTint = Color(0xFFE91E63),
                title = "txamusic_settings_section_visual".txa(),
                subtitle = "txamusic_settings_section_visual_desc".txa(),
                onClick = { onNavigate(SettingsRoute.Visual) }
            )
        }
        item {
            CategoryCard(
                icon = Icons.Outlined.GraphicEq,
                iconTint = Color(0xFF2196F3),
                title = "txamusic_settings_section_audio".txa(),
                subtitle = "txamusic_settings_section_audio_desc".txa(),
                onClick = { onNavigate(SettingsRoute.Audio) }
            )
        }
        item {
            CategoryCard(
                icon = Icons.Outlined.PlayCircleOutline,
                iconTint = Color(0xFFFF9800),
                title = "txamusic_settings_section_now_playing".txa(),
                subtitle = "txamusic_settings_section_now_playing_desc".txa(),
                onClick = { onNavigate(SettingsRoute.NowPlaying) }
            )
        }
        item {
            CategoryCard(
                icon = Icons.Outlined.Dashboard,
                iconTint = Color(0xFF9C27B0),
                title = "txamusic_settings_section_personalize".txa(),
                subtitle = "txamusic_settings_section_personalize_desc".txa(),
                onClick = { onNavigate(SettingsRoute.Personalize) }
            )
        }
        item {
            CategoryCard(
                icon = Icons.Outlined.Image,
                iconTint = Color(0xFF00BCD4),
                title = "txamusic_settings_section_images".txa(),
                subtitle = "txamusic_settings_section_images_desc".txa(),
                onClick = { onNavigate(SettingsRoute.Images) }
            )
        }
        item {
            CategoryCard(
                icon = Icons.Outlined.Build,
                iconTint = Color(0xFF607D8B),
                title = "txamusic_settings_section_other".txa(),
                subtitle = "txamusic_settings_section_other_desc".txa(),
                onClick = { onNavigate(SettingsRoute.Backup) }
            )
        }
        if (com.txapp.musicplayer.util.TXASuHelper.isRooted()) {
            item {
                CategoryCard(
                    icon = Icons.Outlined.Bolt,
                    iconTint = Color(android.graphics.Color.parseColor("#00D269")),
                    title = "txamusic_settings_section_power".txa(),
                    subtitle = "txamusic_settings_section_power_desc".txa(),
                    onClick = { onNavigate(SettingsRoute.Power) }
                )
            }
        }
        item {
            CategoryCard(
                icon = Icons.Outlined.SystemUpdate,
                iconTint = Color(0xFF4CAF50),
                title = "txamusic_settings_section_update_info".txa(),
                subtitle = "txamusic_settings_section_update_info_desc".txa(),
                onClick = { onNavigate(SettingsRoute.About) }
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// --- Sub-Screens ---

@Composable
fun VisualSettings(onLanguageClick: () -> Unit, onThemeClick: () -> Unit, onAccentClick: () -> Unit, selectedLanguage: String) {
    val themeMode by TXAPreferences.theme.collectAsState()
    val accentHex by TXAPreferences.accentColor.collectAsState()
    val isHolidayEnabled by TXAPreferences.holidayEffectEnabled.collectAsState()

    SettingsSubColumn {
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Language,
                title = "txamusic_settings_language".txa(),
                subtitle = "txamusic_lang_$selectedLanguage".txa(),
                onClick = onLanguageClick
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.DarkMode,
                title = "txamusic_settings_theme_title".txa(),
                subtitle = themeMode.replaceFirstChar { it.uppercase() },
                onClick = onThemeClick
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.ColorLens,
                title = "txamusic_settings_accent_title".txa(),
                subtitle = accentHex.uppercase(),
                onClick = onAccentClick
            )
        }
        item {
             // Holiday Effect Toggle with Switch UI style
             Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { 
                        TXAPreferences.isHolidayEffectEnabled = !isHolidayEnabled
                    },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Star, // Use Star as generic festive icon
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "txamusic_settings_holiday_effect".txa(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if(isHolidayEnabled) "On" else "Off",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = isHolidayEnabled,
                        onCheckedChange = { TXAPreferences.isHolidayEffectEnabled = it }
                    )
                }
            }
        }
        
         // DEV: Test Button for Holiday Logic
        item {
             val context = LocalContext.current
             Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { 
                         val cal = java.util.Calendar.getInstance()
                         val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
                         val month = cal.get(java.util.Calendar.MONTH) + 1
                         val year = cal.get(java.util.Calendar.YEAR)
                         
                         val lunar = com.txapp.musicplayer.util.LunarCalendar.getStrictTetRangeDate(day, month, year)
                         val mode = com.txapp.musicplayer.util.TXAHolidayManager.getHolidayMode()
                         
                         val msg = if (lunar == null) {
                             "Solar: $day/$month/$year -> NOT Tet\nMode: $mode"
                         } else {
                             "Solar: $day/$month/$year -> Lunar: ${lunar.day}/${lunar.month}\nMode: $mode"
                         }
                         
                         TXAToast.info(context, msg)
                    },
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BugReport, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Test Holiday Logic (Dev)", 
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AudioSettings(
    onNavigateToEqualizer: () -> Unit
) {
    val context = LocalContext.current
    val crossfadeDuration by TXAPreferences.crossfadeDuration.collectAsState()
    val audioFocus by TXAPreferences.audioFocus.collectAsState()
    val bluetoothPlayback by TXAPreferences.bluetoothPlayback.collectAsState()
    val headsetPlay by TXAPreferences.headsetPlay.collectAsState()
    val audioFadeDuration by TXAPreferences.audioFadeDuration.collectAsState()
    val playbackSpeed by TXAPreferences.playbackSpeed.collectAsState()
    
    var showCrossfadeDialog by remember { mutableStateOf(false) }
    var showAudioFadeDialog by remember { mutableStateOf(false) }
    var showPlaybackSpeedDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    val equalizerEnabled by remember { mutableStateOf(TXAPreferences.isEqualizerEnabled) }

        val hasBluetooth = remember { TXADeviceInfo.hasBluetooth() }

    SettingsSubColumn {
        // 0. Playback Speed - NEW!
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Speed,
                title = "txamusic_playback_speed".txa(),
                subtitle = String.format("%.2fx", playbackSpeed) + " - " + when {
                    playbackSpeed < 0.8f -> "txamusic_speed_slower".txa()
                    playbackSpeed > 1.2f -> "txamusic_speed_faster".txa()
                    else -> "txamusic_speed_normal".txa()
                },
                longPressDesc = "txamusic_settings_playback_speed_desc".txa(),
                onClick = { showPlaybackSpeedDialog = true }
            )
        }

        // 1. Audio Fade Duration
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.GraphicEq,
                title = "txamusic_settings_audio_fade_title".txa(),
                subtitle = if (audioFadeDuration > 0) "$audioFadeDuration ms" else "txamusic_settings_fade_off".txa(),
                longPressDesc = "txamusic_settings_audio_fade_dialog_desc".txa(),
                onClick = { showAudioFadeDialog = true }
            )
        }

        // 2. Manage Audio Focus
        item {
            SettingsSwitchItem(
                icon = TXAIcons.VolumeUp,
                title = "txamusic_settings_audio_focus_title".txa(),
                checked = audioFocus,
                longPressDesc = "txamusic_settings_audio_focus_desc".txa(),
                onCheckedChange = { TXAPreferences.isAudioFocusEnabled = it }
            )
        }

        // 3. Crossfade Duration
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.SyncAlt,
                title = "txamusic_settings_fade_title".txa(),
                subtitle = if (crossfadeDuration > 0) "$crossfadeDuration ${"txamusic_unit_second".txa()}" else "txamusic_settings_fade_off".txa(),
                longPressDesc = "txamusic_settings_fade_dialog_desc".txa(),
                onClick = { showCrossfadeDialog = true }
            )
        }
        
        // 4. Equalizer
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Equalizer,
                title = "txamusic_settings_eq_title".txa(),
                subtitle = if (equalizerEnabled) "txamusic_eq_on".txa() else "txamusic_settings_eq_desc".txa(),
                longPressDesc = "txamusic_settings_eq_desc".txa(),
                onClick = { onNavigateToEqualizer() }
            )
        }

        // 5. Headset Play
        item {
            SettingsSwitchItem(
                icon = Icons.Outlined.Headphones,
                title = "txamusic_settings_headset_title".txa(),
                checked = headsetPlay,
                longPressDesc = "txamusic_settings_headset_desc".txa(),
                onCheckedChange = { enabled ->
                    TXAPreferences.isHeadsetPlayEnabled = enabled
                    // Broadcast to MusicService to re-check audio routing
                    val intent = Intent("com.txapp.musicplayer.action.AUDIO_ROUTE_SETTING_CHANGED")
                    intent.putExtra("type", "headset")
                    intent.putExtra("enabled", enabled)
                    context.sendBroadcast(intent)
                }
            )
        }

        // 6. Bluetooth Playback
        if (hasBluetooth) {
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Bluetooth,
                    title = "txamusic_settings_bluetooth_title".txa(),
                    checked = bluetoothPlayback,
                    longPressDesc = "txamusic_settings_bluetooth_desc".txa(),
                    onCheckedChange = { enabled ->
                        TXAPreferences.isBluetoothPlaybackEnabled = enabled
                        // Broadcast to MusicService to re-check audio routing
                        val intent = Intent("com.txapp.musicplayer.action.AUDIO_ROUTE_SETTING_CHANGED")
                        intent.putExtra("type", "bluetooth")
                        intent.putExtra("enabled", enabled)
                        context.sendBroadcast(intent)
                    }
                )
            }
        }
        
        // 7. Alarms & Reminders Permission (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            item {
                val hasAlarmPermission = remember(LocalLifecycleOwner.current) {
                    mutableStateOf(TXAPermissionHelper.hasExactAlarmPermission(context))
                }
                
                // Refresh permission status on resume
                DisposableEffect(LocalLifecycleOwner.current) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasAlarmPermission.value = TXAPermissionHelper.hasExactAlarmPermission(context)
                        }
                    }
                    val lifecycle = (context as? ComponentActivity)?.lifecycle
                    lifecycle?.addObserver(observer)
                    onDispose {
                        lifecycle?.removeObserver(observer)
                    }
                }

                SettingsToggleCard(
                    icon = Icons.Outlined.Alarm,
                    title = "txamusic_permission_alarm_title".txa(),
                    subtitle = if (hasAlarmPermission.value) "txamusic_permission_status_granted".txa() else "txamusic_permission_alarm_desc".txa(),
                    longPressDesc = "txamusic_permission_alarm_desc".txa(),
                    onClick = { TXAPermissionHelper.requestExactAlarmPermission(context as android.app.Activity) }
                )
            }
        }


        }

    if (showCrossfadeDialog) {
        CrossfadeDialog(
            currentDuration = crossfadeDuration,
            onSelect = { 
                TXAPreferences.currentCrossfadeDuration = it
                showCrossfadeDialog = false
            },
            onDismiss = { showCrossfadeDialog = false }
        )
    }

    if (showAudioFadeDialog) {
        AudioFadeDialog(
            currentDuration = audioFadeDuration,
            onSelect = { 
                TXAPreferences.currentAudioFadeDuration = it
                showAudioFadeDialog = false
            },
            onDismiss = { showAudioFadeDialog = false }
        )
    }
    
    if (showPlaybackSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = playbackSpeed,
            onSelect = { 
                TXAPreferences.currentPlaybackSpeed = it
                showPlaybackSpeedDialog = false
            },
            onDismiss = { showPlaybackSpeedDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFadeDialog(
    currentDuration: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var duration by remember { mutableStateOf(currentDuration.toFloat()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "txamusic_settings_audio_fade_title".txa(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "txamusic_settings_audio_fade_dialog_desc".txa(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = if (duration.toInt() > 0) "${duration.toInt()} ms" else "txamusic_settings_fade_off".txa(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Slider(
                    value = duration,
                    onValueChange = { duration = it },
                    valueRange = 0f..2000f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth(),
                    thumb = {
                        Box(contentAlignment = Alignment.TopCenter) {
                            Box(
                                modifier = Modifier
                                    .offset(y = (-35).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${duration.toInt()} ms",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Off", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("1000ms", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("2000ms", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { duration = 0f }) {
                        Text("txamusic_settings_fade_off".txa()) 
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("txamusic_btn_cancel".txa())
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSelect(duration.toInt()) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("txamusic_btn_confirm".txa())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossfadeDialog(
    currentDuration: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var duration by remember { mutableStateOf(currentDuration.toFloat()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "txamusic_settings_fade_title".txa(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "txamusic_settings_fade_dialog_desc".txa(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = if (duration.toInt() > 0) "${duration.toInt()} ${"txamusic_unit_second".txa()}" else "txamusic_settings_fade_off".txa(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Slider(
                    value = duration,
                    onValueChange = { duration = it },
                    valueRange = 0f..15f,
                    steps = 14,
                    modifier = Modifier.fillMaxWidth(),
                    thumb = {
                        Box(contentAlignment = Alignment.TopCenter) {
                            Box(
                                modifier = Modifier
                                    .offset(y = (-35).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${duration.toInt()} s",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Off", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("6s", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("12s", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { duration = 0f }) {
                        Text("txamusic_settings_fade_off".txa()) 
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("txamusic_btn_cancel".txa())
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSelect(duration.toInt()) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("txamusic_btn_confirm".txa())
                    }
                }
            }
        }
    }
}

@Composable
fun NowPlayingSettings() {
    val context = LocalContext.current
    val showShuffle by TXAPreferences.showShuffleBtn.collectAsState()
    val showFavorite by TXAPreferences.showFavoriteBtn.collectAsState()
    val playbackSpeed by TXAPreferences.playbackSpeed.collectAsState()
    val playerEffectsEnabled by TXAPreferences.playerEffectsEnabled.collectAsState()
    val playerEffectType by TXAPreferences.playerEffectType.collectAsState()



    
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }
    var showEffectDialog by remember { mutableStateOf(false) }
    var currentStyle by remember { mutableStateOf(TXAPreferences.getNowPlayingUI()) }
    val nowPlayingUIState by TXAPreferences.nowPlayingUI.collectAsState()
    
    // Sync local state with global pref
    LaunchedEffect(nowPlayingUIState) {
        currentStyle = nowPlayingUIState
    }

    SettingsSubColumn {
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Style,
                title = "txamusic_settings_np_style_title".txa(),
                subtitle = getNowPlayingStyleName(currentStyle),
                onClick = { showStyleDialog = true }
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Speed,
                title = "txamusic_settings_playback_speed_title".txa(),
                subtitle = "${playbackSpeed}x",
                onClick = { showSpeedDialog = true }
            )
        }
        // Player Effects Toggle - Only show on Android 15+ (API 35)
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "txamusic_settings_player_effects".txa(),
                    checked = playerEffectsEnabled,
                    onCheckedChange = { TXAPreferences.isPlayerEffectsEnabled = it }
                )
            }
            // Player Effect Type Selector
            if (playerEffectsEnabled) {
                item {
                    SettingsToggleCard(
                        icon = Icons.Outlined.Palette,
                        title = "txamusic_settings_player_effect_type".txa(),
                        subtitle = getPlayerEffectTypeName(playerEffectType),
                        onClick = { showEffectDialog = true }
                    )
                }
            }
        }
        item {
            SettingsSwitchItem(
                icon = Icons.Outlined.Shuffle,
                title = "txamusic_settings_show_shuffle".txa(),
                subtitle = "txamusic_settings_show_shuffle_desc".txa(),
                checked = showShuffle,
                onCheckedChange = { TXAPreferences.isShowShuffleBtn = it }
            )
        }
        item {
            SettingsSwitchItem(
                icon = Icons.Outlined.FavoriteBorder,
                title = "txamusic_settings_show_favorite".txa(),
                subtitle = "txamusic_settings_show_favorite_desc".txa(),
                checked = showFavorite,
                onCheckedChange = { TXAPreferences.isShowFavoriteBtn = it }
            )
        }

        // Extra Controls (prev/next buttons on MiniPlayer)
        item {
            val extraControls by TXAPreferences.extraControls.collectAsState()
            SettingsSwitchItem(
                icon = Icons.Outlined.SkipNext,
                title = "txamusic_settings_extra_controls".txa(),
                checked = extraControls,
                longPressDesc = "txamusic_settings_extra_controls_desc".txa(),
                onCheckedChange = { TXAPreferences.isExtraControls = it }
            )
        }
        
        // ═══════════════════════════════════════════════════════════════
        // VISUALIZER SETTINGS
        // ═══════════════════════════════════════════════════════════════
        item {
            val visualizerEnabled by TXAPreferences.visualizerEnabled.collectAsState()
            SettingsSwitchItem(
                icon = Icons.Outlined.Equalizer,
                title = "txamusic_settings_visualizer_title".txa(),
                checked = visualizerEnabled,
                longPressDesc = "txamusic_settings_visualizer_desc".txa(),
                onCheckedChange = { TXAPreferences.isVisualizerEnabled = it }
            )
        }
        
        // Visualizer Style Selector (only show if enabled)
        item {
            val visualizerEnabled by TXAPreferences.visualizerEnabled.collectAsState()
            val visualizerStyle by TXAPreferences.visualizerStyle.collectAsState()
            var showVisualizerStyleDialog by remember { mutableStateOf(false) }
            
            if (visualizerEnabled) {
                SettingsToggleCard(
                    icon = Icons.Outlined.Waves,
                    title = "txamusic_settings_visualizer_style".txa(),
                    subtitle = getVisualizerStyleName(visualizerStyle),
                    onClick = { showVisualizerStyleDialog = true }
                )
                
                if (showVisualizerStyleDialog) {
                    VisualizerStyleDialog(
                        currentStyle = visualizerStyle,
                        onSelect = { style ->
                            TXAPreferences.currentVisualizerStyle = style
                            showVisualizerStyleDialog = false
                        },
                        onDismiss = { showVisualizerStyleDialog = false }
                    )
                }
            }
        }
        
        // Floating Lyrics Overlay Toggle with Permission Check
        // Hide on emulator as overlay doesn't work properly
        if (!TXADeviceInfo.isEmulator()) {
            item {
                var showOverlayPermissionDialog by remember { mutableStateOf(false) }
                val showLyricsInPlayer by TXAPreferences.showLyricsInPlayer.collectAsState()
                val hasOverlayPermission = remember(LocalLifecycleOwner.current) {
                    mutableStateOf(Settings.canDrawOverlays(context))
                }
            
            // Refresh permission status on resume
            DisposableEffect(LocalLifecycleOwner.current) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasOverlayPermission.value = Settings.canDrawOverlays(context)
                    }
                }
                val lifecycle = (context as? ComponentActivity)?.lifecycle
                lifecycle?.addObserver(observer)
                onDispose { lifecycle?.removeObserver(observer) }
            }
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (hasOverlayPermission.value) {
                            TXAPreferences.setShowLyricsInPlayer(!showLyricsInPlayer)
                        } else {
                            showOverlayPermissionDialog = true
                        }
                    },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Subtitles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "txamusic_show_lyrics_overlay".txa(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "txamusic_show_lyrics_overlay_desc".txa(),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    if (!hasOverlayPermission.value) {
                        // Show grant button if no permission
                        OutlinedButton(
                            onClick = { showOverlayPermissionDialog = true },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("txamusic_btn_grant".txa(), fontSize = 12.sp)
                        }
                    } else {
                        Switch(
                            checked = showLyricsInPlayer,
                            onCheckedChange = { enabled ->
                                TXAPreferences.setShowLyricsInPlayer(enabled)
                                if (enabled && !com.txapp.musicplayer.util.TXADeviceInfo.isEmulator()) {
                                    com.txapp.musicplayer.service.FloatingLyricsService.startService(context)
                                } else {
                                    com.txapp.musicplayer.service.FloatingLyricsService.stopService(context)
                                }
                            }
                        )
                    }
                }
            }
            
            // Overlay Permission Dialog
            if (showOverlayPermissionDialog) {
                AlertDialog(
                    onDismissRequest = { showOverlayPermissionDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = { Text("txamusic_overlay_permission_title".txa()) },
                    text = { Text("txamusic_overlay_permission_desc".txa()) },
                    confirmButton = {
                        Button(onClick = {
                            showOverlayPermissionDialog = false
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }) {
                            Text("txamusic_btn_grant".txa())
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOverlayPermissionDialog = false }) {
                            Text("txamusic_btn_cancel".txa())
                        }
                    }
                )
            }
        }
        } // End of if (!TXADeviceInfo.isEmulator())
    }

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = playbackSpeed,
            onSelect = { 
                TXAPreferences.currentPlaybackSpeed = it
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }
    
    if (showStyleDialog) {
        NowPlayingStyleDialog(
            currentStyle = currentStyle,
            onSelect = { style ->
                TXAPreferences.setNowPlayingUI(style)
                currentStyle = style
                showStyleDialog = false
            },
            onDismiss = { showStyleDialog = false }
        )
    }

    if (showEffectDialog) {
        PlayerEffectTypeDialog(
            currentEffect = playerEffectType,
            onSelect = { effect ->
                TXAPreferences.currentPlayerEffectType = effect
                showEffectDialog = false
            },
            onDismiss = { showEffectDialog = false }
        )
    }



}

/**
 * Now Playing Style options - NEW TXA STYLES
 */
enum class NowPlayingStyle(val key: String, val labelKey: String, val icon: ImageVector) {
    AURORA("aurora", "txamusic_np_style_aurora", Icons.Outlined.AutoAwesome),
    GLASS("glass", "txamusic_np_style_glass", Icons.Outlined.BlurOn),
    VINYL("vinyl", "txamusic_np_style_vinyl", Icons.Outlined.Album),
    NEON("neon", "txamusic_np_style_neon", Icons.Outlined.Bolt),

    SPECTRUM("spectrum", "txamusic_np_style_spectrum", Icons.Outlined.Palette),
    FULL("full", "txamusic_np_style_full", Icons.Outlined.Fullscreen)
}

@Composable
fun getNowPlayingStyleName(key: String): String {
    return when (key) {
        "aurora" -> "txamusic_np_style_aurora".txa()
        "glass" -> "txamusic_np_style_glass".txa()
        "vinyl" -> "txamusic_np_style_vinyl".txa()
        "neon" -> "txamusic_np_style_neon".txa()

        "spectrum" -> "txamusic_np_style_spectrum".txa()
        "full" -> "txamusic_np_style_full".txa()
        else -> "txamusic_np_style_aurora".txa()
    }
}

@Composable
fun getPlayerEffectTypeName(key: String): String {
    return when (key) {
        "snow" -> "❄️ Snow"
        "stars" -> "⭐ Stars"
        "bubbles" -> "🫧 Bubbles"
        "sakura" -> "🌸 Sakura"
        "fireflies" -> "✨ Fireflies"
        "rain" -> "🌧️ Rain"
        "confetti" -> "🎊 Confetti"
        "hearts" -> "❤️ Hearts"
        else -> "❄️ Snow"
    }
}

@Composable
fun NowPlayingStyleDialog(
    currentStyle: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "txamusic_settings_np_style_title".txa(),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NowPlayingStyle.entries.size) { index ->
                    val style = NowPlayingStyle.entries[index]
                    val isSelected = style.key == currentStyle
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(style.key) },
                        color = if (isSelected) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                style.icon,
                                contentDescription = null,
                                tint = if (isSelected) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = style.labelKey.txa(),
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (isSelected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("txamusic_btn_cancel".txa())
            }
        }
    )
}

/**
 * Player Effect Type Selection Dialog
 */
@Composable
fun PlayerEffectTypeDialog(
    currentEffect: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val effects = listOf(
        "snow" to "❄️ Snow",
        "stars" to "⭐ Stars",
        "bubbles" to "🫧 Bubbles",
        "sakura" to "🌸 Sakura",
        "fireflies" to "✨ Fireflies",
        "rain" to "🌧️ Rain",
        "confetti" to "🎊 Confetti",
        "hearts" to "❤️ Hearts"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "txamusic_settings_player_effect_type".txa(),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(effects.size) { index ->
                    val (key, label) = effects[index]
                    val isSelected = key == currentEffect
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(key) },
                        color = if (isSelected) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 16.sp,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (isSelected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("txamusic_btn_cancel".txa())
            }
        }
    )
}

/**
 * Get localized name for visualizer style
 */
@Composable
fun getVisualizerStyleName(key: String): String {
    return when (key) {
        "bars" -> "txamusic_visualizer_bars".txa()
        "wave" -> "txamusic_visualizer_wave".txa()
        "circle" -> "txamusic_visualizer_circle".txa()
        "spectrum" -> "txamusic_visualizer_spectrum".txa()
        "glow" -> "txamusic_visualizer_glow".txa()
        "fluid" -> "txamusic_visualizer_fluid".txa()
        else -> "txamusic_visualizer_bars".txa()
    }
}

/**
 * Visualizer Style Selection Dialog
 */
@Composable
fun VisualizerStyleDialog(
    currentStyle: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val styles = listOf(
        "bars" to "📊 " + "txamusic_visualizer_bars".txa(),
        "wave" to "🌊 " + "txamusic_visualizer_wave".txa(),
        "circle" to "🔘 " + "txamusic_visualizer_circle".txa(),
        "spectrum" to "📈 " + "txamusic_visualizer_spectrum".txa(),
        "glow" to "✨ " + "txamusic_visualizer_glow".txa(),
        "fluid" to "💦 " + "txamusic_visualizer_fluid".txa()
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "txamusic_settings_visualizer_style".txa(),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(styles.size) { index ->
                    val (key, label) = styles[index]
                    val isSelected = key == currentStyle
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(key) },
                        color = if (isSelected) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 16.sp,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (isSelected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("txamusic_btn_cancel".txa())
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    longPressDesc: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onCheckedChange(!checked) },
                onLongClick = {
                    longPressDesc?.let { TXAToast.info(context, it) }
                }
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var speed by remember { mutableStateOf(currentSpeed) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "txamusic_settings_playback_speed_title".txa(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "${String.format("%.2f", speed)}x",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 0.5f..2.0f,
                    steps = 14, // 0.1 increments roughly
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0.5x", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("1.0x", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("2.0x", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { speed = 1.0f }) {
                        Text("Reset") 
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("txamusic_btn_cancel".txa())
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSelect(speed) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("txamusic_btn_confirm".txa())
                    }
                }
            }
        }
    }
}

@Composable
fun PersonalizeSettings(
    onGridClick: () -> Unit,
    onAlbumGridClick: () -> Unit,
    onArtistGridClick: () -> Unit,
    onClearHistoryClick: () -> Unit, // Callback to open dialog
    onBlacklistClick: () -> Unit,
    blacklistedFolders: List<com.txapp.musicplayer.data.BlackListEntity> = emptyList()
) {

    val gridSize by TXAPreferences.gridSize.collectAsState()
    val rememberLastTab by TXAPreferences.rememberLastTab.collectAsState()
    val albumGridSize by TXAPreferences.albumGridSize.collectAsState()
    val artistGridSize by TXAPreferences.artistGridSize.collectAsState()
    val context = LocalContext.current
    
    SettingsSubColumn {
        item {
            SettingsSwitchItem(
                icon = Icons.Outlined.Save,
                title = "txamusic_settings_remember_last_tab".txa(),
                subtitle = "txamusic_settings_remember_last_tab_desc".txa(),
                checked = rememberLastTab,
                onCheckedChange = { TXAPreferences.isRememberLastTab = it }
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.GridView,
                title = "txamusic_settings_album_grid_size".txa(),
                subtitle = "$albumGridSize Columns",
                onClick = onAlbumGridClick
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.AccountCircle,
                title = "txamusic_settings_artist_grid_size".txa(),
                subtitle = "$artistGridSize Columns",
                onClick = onArtistGridClick
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Widgets,
                title = "txamusic_widget_settings".txa(),
                subtitle = "txamusic_widget_settings_desc".txa(),
                onClick = {
                    val intent = Intent(context, com.txapp.musicplayer.appwidget.WidgetSettingsActivity::class.java)
                    context.startActivity(intent)
                }
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Style,
                title = "txamusic_settings_grid_title".txa(),
                subtitle = "$gridSize Columns",
                onClick = onGridClick
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Refresh,
                title = "txamusic_settings_refresh_playlists".txa(),
                subtitle = "txamusic_settings_refresh_playlists_desc".txa(),
                onClick = {
                    val activity = context as? androidx.activity.ComponentActivity
                    if (activity is MainActivity) {
                        activity.triggerMediaStoreScan()
                        TXAToast.success(context, "Scanning for changes...")
                    } else {
                        // Fallback: send broadcast
                        val intent = Intent("com.txapp.musicplayer.action.TRIGGER_RESCAN")
                        context.sendBroadcast(intent)
                        TXAToast.info(context, "Rescan triggered")
                    }
                }
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.FolderOff,
                title = "txamusic_blacklist_folders".txa(),
                subtitle = "txamusic_blacklist_folder_desc".txa(),
                onClick = onBlacklistClick
            )
            
            if (blacklistedFolders.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "txamusic_folder_blacklisted".txa().uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    blacklistedFolders.forEach { folder ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = folder.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        
        // Remember Playback Position UI
        item {
            val rememberPosition by TXAPreferences.rememberPlaybackPosition.collectAsState()
            
            // Refresh count
            var savedCount by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    com.txapp.musicplayer.util.TXAPlaybackHistory.load(context)
                    savedCount = com.txapp.musicplayer.util.TXAPlaybackHistory.getSavedCount()
                }
            }

            SettingsSwitchItem(
                icon = Icons.Outlined.Restore, // Use Outlined.Restore or AutoMirrored if available, but Outlined is safe
                title = "txamusic_settings_remember_pos".txa(),
                subtitle = "txamusic_settings_remember_pos_desc".txa(), 
                checked = rememberPosition,
                onCheckedChange = { TXAPreferences.isRememberPlaybackPositionEnabled = it }
            )
            
            if (rememberPosition && savedCount > 0) {
                 SettingsToggleCard(
                     icon = Icons.Outlined.History, 
                     title = "txamusic_settings_clear_history".txa(), 
                     subtitle = "txamusic_settings_history_count".txa(savedCount),
                     onClick = { onClearHistoryClick() }
                 )
            }
        }
    }
}

@Composable
fun ImageSettings() {
    val context = LocalContext.current
    val autoDownload by TXAPreferences.autoDownloadImages.collectAsState()
    
    SettingsSubColumn {
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.HighQuality,
                title = "txamusic_settings_image_quality_title".txa(),
                subtitle = TXAPreferences.getImageQuality().replaceFirstChar { it.uppercase() },
                onClick = {
                    // Cycle through: low -> medium -> high -> low
                    val current = TXAPreferences.getImageQuality()
                    val next = when (current) {
                        "low" -> "medium"
                        "medium" -> "high"
                        else -> "low"
                    }
                    TXAPreferences.setImageQuality(next)
                    TXAToast.info(context, "Image Quality: ${next.replaceFirstChar { it.uppercase() }}")
                }
            )
        }
        item {
            // Auto Download Toggle with Switch
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { 
                        TXAPreferences.isAutoDownloadImagesEnabled = !autoDownload
                    },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ImageSearch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "txamusic_settings_auto_download_title".txa(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "txamusic_settings_auto_download_desc".txa(),
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = autoDownload,
                        onCheckedChange = { TXAPreferences.isAutoDownloadImagesEnabled = it }
                    )
                }
            }
        }
    }
}

@Composable
fun PowerSettings() {
    val context = LocalContext.current
    val powerMode by TXAPreferences.powerMode.collectAsState()
    val aodAutoBrightness by TXAPreferences.aodAutoBrightness.collectAsState()
    val isRooted = remember { TXASuHelper.isRooted() }

    SettingsSubColumn {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "txamusic_settings_power_tip".txa(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Root Optimization Toggle
        item {
            SettingsSwitchItem(
                icon = Icons.Default.Security,
                title = "txamusic_settings_root_power".txa(),
                checked = powerMode,
                longPressDesc = "txamusic_settings_root_power_desc".txa(),
                onCheckedChange = { 
                    if (!isRooted && it) {
                        TXAToast.error(context, "txamusic_info_root_status".txa("txamusic_no".txa()))
                    } else {
                        TXAPreferences.isPowerMode = it
                    }
                }
            )
        }

        // AOD Auto Brightness Toggle
        item {
            SettingsSwitchItem(
                icon = Icons.Outlined.BrightnessAuto,
                title = "txamusic_settings_aod_brightness".txa(),
                checked = aodAutoBrightness,
                longPressDesc = "txamusic_settings_aod_brightness_desc".txa(),
                onCheckedChange = { 
                    TXAPreferences.isAodAutoBrightness = it
                }
            )
        }
        
        // Write Settings Permission Shortcut
        item {
            val hasWriteSettings = remember(LocalLifecycleOwner.current) {
                mutableStateOf(TXASystemSettingsHelper.canWriteSettings(context))
            }
            
            // Refresh status
            DisposableEffect(LocalLifecycleOwner.current) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasWriteSettings.value = TXASystemSettingsHelper.canWriteSettings(context)
                    }
                }
                val lifecycle = (context as? ComponentActivity)?.lifecycle
                lifecycle?.addObserver(observer)
                onDispose { lifecycle?.removeObserver(observer) }
            }

            SettingsToggleCard(
                icon = Icons.Outlined.SettingsSuggest,
                title = "txamusic_settings_write_permission".txa(),
                subtitle = if (hasWriteSettings.value) "txamusic_permission_status_granted".txa() else "txamusic_settings_write_permission_desc".txa(),
                onClick = { TXASystemSettingsHelper.requestWriteSettingsPermission(context) }
            )
        }
    }
}

/**
 * Build restore result message with skipped count if needed
 */
private fun buildRestoreResultMessage(result: TXABackupHelper.RestoreResult): String {
    val sb = StringBuilder()
    sb.append("txamusic_restore_result_success".txa(
        result.favoritesRestored,
        result.historyRestored,
        result.playlistsRestored,
        if (result.settingsRestored) "✓" else "✗"
    ))
    
    // Add skipped info if any (combine favorites + history + playlists skipped)
    val totalSkipped = result.favoritesSkipped + result.historySkipped + result.playlistsSkipped
    if (totalSkipped > 0) {
        sb.append("\n")
        sb.append("txamusic_restore_skipped".txa(totalSkipped))
    }
    
    return sb.toString()
}

/**
 * Backup & Restore Settings - Dedicated screen for backup management
 */
@Composable
fun BackupRestoreSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Backup states
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var resultSuccess by remember { mutableStateOf(false) }
    
    // Delete confirmation state
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteFile by remember { mutableStateOf<File?>(null) }
    
    // Restore warning state
    var showRestoreWarningDialog by remember { mutableStateOf(false) }
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    
    // Rename state
    var showRenameDialog by remember { mutableStateOf(false) }
    var pendingRenameFile by remember { mutableStateOf<File?>(null) }
    var renameValue by remember { mutableStateOf("") }
    
    val isBackingUp by TXABackupHelper.isBackingUp.collectAsState()
    val isRestoring by TXABackupHelper.isRestoring.collectAsState()
    val progress by TXABackupHelper.progress.collectAsState()
    val statusMessage by TXABackupHelper.statusMessage.collectAsState()
    
    // File picker for restore
    val restoreFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                showProgressDialog = true
                val result = TXABackupHelper.restoreBackup(context, it)
                showProgressDialog = false
                
                resultSuccess = result.success
                resultMessage = if (result.success) {
                    buildRestoreResultMessage(result)
                } else {
                    "txamusic_restore_result_failed".txa(result.error ?: "Unknown error")
                }
                showResultDialog = true
            }
        }
    }

    // Get backup files with real-time refresh
    var refreshTrigger by remember { mutableIntStateOf(0) }
    
    // Refresh when screen is resumed
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val backupFiles = remember(refreshTrigger) { 
        TXABackupHelper.getBackupFiles(context)
            .filter { it.isFile && it.extension.lowercase() == "txa" }
            .sortedByDescending { it.lastModified() }
            .toMutableList() 
    }
    val lastBackup = backupFiles.firstOrNull()
    val lastBackupDate = lastBackup?.let {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it.lastModified()))
    }
    
    // Helper function to refresh backup list
    fun refreshBackupList() {
        refreshTrigger++
    }
    
    SettingsSubColumn {
        // Info Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "txamusic_backup_dialog_desc".txa(),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(8.dp)) }
        
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Backup,
                title = "txamusic_settings_backup_title".txa(),
                subtitle = if (lastBackupDate != null) 
                    "txamusic_backup_last".txa(lastBackupDate)
                else 
                    "txamusic_settings_backup_desc".txa(),
                isLoading = isBackingUp,
                onClick = { showBackupDialog = true }
            )
        }
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.SettingsBackupRestore,
                title = "txamusic_settings_restore_title".txa(),
                subtitle = "txamusic_settings_restore_desc".txa(),
                isLoading = isRestoring,
                onClick = { showRestoreDialog = true }
            )
        }
        
        // Show existing backups in scrollable container
        if (backupFiles.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "txamusic_backup_existing".txa(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
                
                // Responsive height container for backup files
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 0.dp, max = 400.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        backupFiles.take(20).forEach { file -> // Show up to 20 in list
                            BackupFileItem(
                                file = file,
                                onRestore = {
                                    // Check if backup has different data than current
                                    scope.launch {
                                        val hasDifference = TXABackupHelper.compareBackupWithCurrent(context, file)
                                        if (hasDifference) {
                                            // Show warning dialog
                                            pendingRestoreFile = file
                                            showRestoreWarningDialog = true
                                        } else {
                                            // No difference, proceed directly
                                            showProgressDialog = true
                                            val result = TXABackupHelper.restoreBackup(context, file)
                                            showProgressDialog = false
                                            
                                            resultSuccess = result.success
                                            resultMessage = if (result.success) {
                                                buildRestoreResultMessage(result)
                                            } else {
                                                "txamusic_restore_result_failed".txa(result.error ?: "Unknown error")
                                            }
                                            showResultDialog = true
                                        }
                                    }
                                },
                                onDelete = {
                                    // Show confirmation dialog
                                    pendingDeleteFile = file
                                    showDeleteConfirmDialog = true
                                },
                                onRename = {
                                    pendingRenameFile = file
                                    renameValue = file.nameWithoutExtension.removePrefix("TXA_")
                                    showRenameDialog = true
                                },
                                onShare = {
                                    shareBackupFile(context, file)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Backup Dialog
    if (showBackupDialog) {
        BackupContentDialog(
            onDismiss = { showBackupDialog = false },
            onConfirm = { selectedContents, customName ->
                showBackupDialog = false
                scope.launch {
                    showProgressDialog = true
                    val result = TXABackupHelper.createBackup(context, selectedContents, customName)
                    showProgressDialog = false
                    
                    resultSuccess = result.success
                    resultMessage = if (result.success) {
                        "txamusic_backup_result_success".txa(result.filePath ?: "")
                    } else {
                        "txamusic_backup_result_failed".txa(result.error ?: "Unknown error")
                    }
                    showResultDialog = true
                    
                    // Refresh backup list after creating
                    if (result.success) {
                        refreshBackupList()
                    }
                }
            }
        )
    }
    
    // Restore Dialog
    if (showRestoreDialog) {
        RestoreSourceDialog(
            onDismiss = { showRestoreDialog = false },
            onSelectFile = {
                showRestoreDialog = false
                restoreFilePicker.launch(arrayOf("*/*"))
            },
            existingBackups = backupFiles,
            onSelectExisting = { file ->
                showRestoreDialog = false
                scope.launch {
                    showProgressDialog = true
                    val result = TXABackupHelper.restoreBackup(context, file)
                    showProgressDialog = false
                    
                    resultSuccess = result.success
                    resultMessage = if (result.success) {
                        buildRestoreResultMessage(result)
                    } else {
                        "txamusic_restore_result_failed".txa(result.error ?: "Unknown error")
                    }
                    showResultDialog = true
                }
            }
        )
    }
    
    // Progress Dialog
    if (showProgressDialog) {
        BackupProgressDialog(
            progress = progress,
            message = statusMessage,
            isBackup = isBackingUp
        )
    }
    
    // Result Dialog
    if (showResultDialog) {
        BackupResultDialog(
            success = resultSuccess,
            message = resultMessage,
            onDismiss = { showResultDialog = false }
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && pendingDeleteFile != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false
                pendingDeleteFile = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("txamusic_delete_confirm_title".txa())
            },
            text = {
                Text("txamusic_delete_confirm_desc".txa(pendingDeleteFile?.name ?: ""))
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteFile?.let { file ->
                            TXABackupHelper.deleteBackup(file)
                            TXAToast.success(context, "txamusic_backup_deleted".txa())
                            refreshBackupList()
                        }
                        showDeleteConfirmDialog = false
                        pendingDeleteFile = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("txamusic_action_delete".txa())
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        pendingDeleteFile = null
                    }
                ) {
                    Text("txamusic_action_cancel".txa())
                }
            }
        )
    }
    
    // Restore Warning Dialog
    if (showRestoreWarningDialog && pendingRestoreFile != null) {
        AlertDialog(
            onDismissRequest = { 
                showRestoreWarningDialog = false
                pendingRestoreFile = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("txamusic_restore_warning_title".txa())
            },
            text = {
                Text("txamusic_restore_warning_desc".txa())
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = pendingRestoreFile
                        showRestoreWarningDialog = false
                        pendingRestoreFile = null
                        
                        if (file != null) {
                            scope.launch {
                                showProgressDialog = true
                                val result = TXABackupHelper.restoreBackup(context, file)
                                showProgressDialog = false
                                
                                resultSuccess = result.success
                                resultMessage = if (result.success) {
                                    buildRestoreResultMessage(result)
                                } else {
                                    "txamusic_restore_result_failed".txa(result.error ?: "Unknown error")
                                }
                                showResultDialog = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("txamusic_action_confirm".txa())
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRestoreWarningDialog = false
                        pendingRestoreFile = null
                    }
                ) {
                    Text("txamusic_action_cancel".txa())
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog && pendingRenameFile != null) {
        AlertDialog(
            onDismissRequest = { 
                showRenameDialog = false
                pendingRenameFile = null
            },
            title = { Text("txamusic_backup_rename".txa()) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("txamusic_backup_rename_hint".txa()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRenameFile?.let { file ->
                            if (TXABackupHelper.renameBackup(file, renameValue)) {
                                TXAToast.success(context, "txamusic_backup_rename_success".txa())
                                refreshBackupList()
                            } else {
                                TXAToast.error(context, "Failed to rename")
                            }
                        }
                        showRenameDialog = false
                        pendingRenameFile = null
                    }
                ) {
                    Text("txamusic_action_confirm".txa())
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    pendingRenameFile = null
                }) {
                    Text("txamusic_action_cancel".txa())
                }
            }
        )
    }
}

@Composable
fun OtherSettings() {
    // This screen has been moved to BackupRestoreSettings
    // Keeping as placeholder for future "Other" settings
    SettingsSubColumn {
        item {
            Text(
                text = "txamusic_settings_other_placeholder".txa(),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun AboutSettings(isChecking: Boolean, onCheckUpdate: () -> Unit, onSocialClick: () -> Unit) {
    val context = LocalContext.current
    
    SettingsSubColumn {
        // Styled App Name & Build info
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                color = Color.Transparent
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val appName = "txamusic_app_name".txa()
                    val parts = appName.split(" ", limit = 2)
                    val annotatedString = buildAnnotatedString {
                        if (parts.isNotEmpty()) {
                            withStyle(style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black,
                                fontSize = 32.sp
                            )) {
                                append(parts[0])
                                if (parts.size > 1) append(" ")
                            }
                        }
                        if (parts.size > 1) {
                            withStyle(style = SpanStyle(
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            )) {
                                append(parts[1])
                            }
                        }
                    }
                    Text(text = annotatedString)
                    Text(
                        text = "Build by TXA",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Update,
                title = "txamusic_settings_check_update".txa(),
                subtitle = "${"txamusic_settings_version".txa()} ${TXADeviceInfo.getVersionName()}",
                isLoading = isChecking,
                onClick = onCheckUpdate
            )
        }
        
        item {
            SettingsToggleCard(
                icon = Icons.AutoMirrored.Outlined.ContactSupport,
                title = "txamusic_settings_contact_title".txa(),
                subtitle = "txamusic_settings_contact_desc".txa(),
                onClick = { sendEmail(context) }
            )
        }
        
        item {
            SettingsToggleCard(
                icon = Icons.Outlined.Share,
                title = "txamusic_settings_section_social".txa(),
                subtitle = "Facebook, YouTube",
                onClick = onSocialClick
            )
        }
        
        // Support Author / Donate
        item {
            SettingsToggleCard(
                icon = ImageVector.vectorResource(com.txapp.musicplayer.R.drawable.ic_buymeacoffee),
                title = "txamusic_settings_support_author".txa(),
                subtitle = "txamusic_settings_support_author_desc".txa(),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/txaro"))
                    context.startActivity(intent)
                }
            )
        }
        
        item {
            DeviceInfoCardContent()
        }
    }
}

@Composable
fun DeviceInfoCardContent() {
    val manufacturer = remember { TXADeviceInfo.getManufacturer() }
    val model = remember { TXADeviceInfo.getModel() }
    val androidVersion = remember { TXADeviceInfo.getAndroidVersion() }
    val sdkVersion = remember { TXADeviceInfo.getSdkVersion() }
    val isEmulator = remember { TXADeviceInfo.isEmulator() }
    val isRooted = remember { TXASuHelper.isRooted() }
    val verifiedRoot = remember { TXASuHelper.getRootStatus() }

    val rootStatus = if (!isRooted) "txamusic_no".txa() 
                     else if (verifiedRoot == true) "txamusic_root_granted".txa()
                     else if (verifiedRoot == false) "txamusic_root_denied".txa()
                     else "Unverified"

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "txamusic_info_device".txa(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Model
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Smartphone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "txamusic_info_model".txa(model),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                         color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Android Version
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "txamusic_info_android".txa("$androidVersion (SDK $sdkVersion)"),
                        style = MaterialTheme.typography.bodyMedium,
                         fontWeight = FontWeight.SemiBold,
                         color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                
                // Emulator Status (only if true)
                if (isEmulator) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                         Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                         Spacer(modifier = Modifier.width(12.dp))
                         Text(
                             text = "txamusic_info_emulator".txa("txamusic_yes".txa()),
                             style = MaterialTheme.typography.bodyMedium,
                             fontWeight = FontWeight.SemiBold,
                             color = MaterialTheme.colorScheme.error
                         )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                // Root Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = if (verifiedRoot == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    val icon = if (verifiedRoot == true) Icons.Outlined.CheckCircle else Icons.Default.Security
                    
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "txamusic_info_root_status".txa(rootStatus),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceInfoRow(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

private fun sendEmail(context: Context) {
    val deviceInfo = TXADeviceInfo.getFullDeviceInfo()
    val subject = "txamusic_contact_email_subject".txa()
    val body = "txamusic_contact_email_body".txa(deviceInfo)
    
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf("txavlog7@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val chooser = Intent.createChooser(intent, "Send email...")
        try {
            context.startActivity(chooser)
        } catch (e2: Exception) {
             TXAToast.error(context, "txamusic_browser_not_found".txa())
        }
    }
}

/**
 * Share backup file via system share sheet
 */
private fun shareBackupFile(context: Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TXA Music Backup: ${file.name}")
            putExtra(Intent.EXTRA_TEXT, "txamusic_share_backup_text".txa())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "txamusic_share_backup_title".txa()))
    } catch (e: Exception) {
        TXAToast.error(context, "Error sharing file: ${e.message}")
    }
}

// --- Common UI Components ---

@Composable
fun SettingsSubColumn(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun CategoryCard(icon: ImageVector, iconTint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isLoading: Boolean = false,
    longPressDesc: String? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                enabled = !isLoading,
                onClick = onClick,
                onLongClick = {
                    longPressDesc?.let { TXAToast.info(context, it) }
                }
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

// --- Dialogs ---

@Composable
fun <T> SelectionDialog(title: String, items: List<T>, selectedItem: T, itemLabel: (T) -> String, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                items.forEach { item ->
                    val isSelected = item == selectedItem
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(item) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isSelected, onClick = { onSelect(item) })
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = itemLabel(item), fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ColorSelectionDialog(selectedColor: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = listOf("#1100ffff", "#4CAF50", "#2196F3", "#E91E63", "#FF9800", "#9C27B0", "#F44336", "#795548")
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Text(text = "txamusic_settings_accent_title".txa(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    colors.take(4).forEach { ColorCircle(it, it == selectedColor, onSelect) }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    colors.drop(4).forEach { ColorCircle(it, it == selectedColor, onSelect) }
                }
            }
        }
    }
}

@Composable
fun ColorCircle(hex: String, isSelected: Boolean, onClick: (String) -> Unit) {
    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex))).clickable { onClick(hex) }, contentAlignment = Alignment.Center) {
        if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
    }
}

@Composable
fun SocialLinksDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val links = listOf(
        "txamusic_social_facebook".txa() to "https://fb.com/vlog.txa.2311",
        "txamusic_social_youtube".txa() to "https://youtube.com/@admintxa?sub_confirmation=1"
    )
    
    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            // Directly start activity and catch exception if no browser found
            // This fixes issue on Android 11+ where resolveActivity returns null due to package visibility
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // Catch exception when no activity found
            TXAToast.error(context, "txamusic_browser_not_found".txa())
        } catch (e: Exception) {
            TXAToast.error(context, "txamusic_error_prefix".txa() + e.message)
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Text(text = "txamusic_settings_section_social".txa(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Facebook
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { openUrl("https://fb.com/vlog.txa.2311") }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.txapp.musicplayer.R.drawable.ic_facebook),
                        contentDescription = "Facebook",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("txamusic_social_facebook".txa(), fontSize = 16.sp)
                }

                // YouTube
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { openUrl("https://youtube.com/@admintxa?sub_confirmation=1") }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(com.txapp.musicplayer.R.drawable.ic_youtube),
                        contentDescription = "YouTube",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("txamusic_social_youtube".txa(), fontSize = 16.sp)
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("txamusic_btn_cancel".txa()) }
            }
        }
    }
}

@Composable
fun LanguageSelectionDialog(availableLanguages: List<TXATranslation.LanguageInfo>, currentLocale: String, isLoading: Boolean, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "txamusic_settings_language".txa(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(16.dp))
                if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                else availableLanguages.forEach { lang ->
                    val isSelected = lang.code == currentLocale
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(lang.code) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isSelected, onClick = { onSelect(lang.code) })
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = lang.displayName, fontSize = 16.sp, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("txamusic_btn_cancel".txa()) }
            }
        }
    }
}

// ============== BACKUP/RESTORE DIALOGS ==============

@Composable
fun BackupFileItem(
    file: File,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(file.lastModified()))
    val fileSize = TXAFormat.formatSize(file.length())
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.nameWithoutExtension.removePrefix("TXA_"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "$formattedDate • $fileSize",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Rename",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun BackupContentDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<TXABackupHelper.BackupContent>, String?) -> Unit
) {
    var selectedContents by remember { 
        mutableStateOf(TXABackupHelper.BackupContent.entries.toSet()) 
    }
    var customName by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "txamusic_backup_dialog_title".txa(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "txamusic_backup_dialog_desc".txa(),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Custom name input
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("txamusic_backup_name".txa()) },
                    placeholder = { Text(TXABackupHelper.getTimeStamp()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "txamusic_backup_select_content".txa(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Content checkboxes
                BackupContentCheckbox(
                    label = "txamusic_backup_favorites".txa(),
                    icon = Icons.Outlined.Favorite,
                    checked = selectedContents.contains(TXABackupHelper.BackupContent.FAVORITES),
                    onCheckedChange = {
                        selectedContents = if (it) 
                            selectedContents + TXABackupHelper.BackupContent.FAVORITES
                        else 
                            selectedContents - TXABackupHelper.BackupContent.FAVORITES
                    }
                )
                
                BackupContentCheckbox(
                    label = "txamusic_backup_history".txa(),
                    icon = Icons.Outlined.History,
                    checked = selectedContents.contains(TXABackupHelper.BackupContent.PLAY_HISTORY),
                    onCheckedChange = {
                        selectedContents = if (it) 
                            selectedContents + TXABackupHelper.BackupContent.PLAY_HISTORY
                        else 
                            selectedContents - TXABackupHelper.BackupContent.PLAY_HISTORY
                    }
                )
                
                BackupContentCheckbox(
                    label = "txamusic_backup_settings".txa(),
                    icon = Icons.Outlined.Settings,
                    checked = selectedContents.contains(TXABackupHelper.BackupContent.SETTINGS),
                    onCheckedChange = {
                        selectedContents = if (it) 
                            selectedContents + TXABackupHelper.BackupContent.SETTINGS
                        else 
                            selectedContents - TXABackupHelper.BackupContent.SETTINGS
                    }
                )
                
                BackupContentCheckbox(
                    label = "txamusic_backup_playlists".txa(),
                    icon = Icons.AutoMirrored.Outlined.QueueMusic, // Or similar playlist icon
                    checked = selectedContents.contains(TXABackupHelper.BackupContent.PLAYLISTS),
                    onCheckedChange = {
                        selectedContents = if (it) 
                            selectedContents + TXABackupHelper.BackupContent.PLAYLISTS
                        else 
                            selectedContents - TXABackupHelper.BackupContent.PLAYLISTS
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("txamusic_btn_cancel".txa())
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            onConfirm(
                                selectedContents.toList(), 
                                customName.ifBlank { null }
                            ) 
                        },
                        enabled = selectedContents.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("txamusic_backup_create".txa())
                    }
                }
            }
        }
    }
}

@Composable
fun BackupContentCheckbox(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp
        )
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun RestoreSourceDialog(
    onDismiss: () -> Unit,
    onSelectFile: () -> Unit,
    existingBackups: List<File>,
    onSelectExisting: (File) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth()
            ) {
                Text(
                    text = "txamusic_restore_dialog_title".txa(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "txamusic_restore_dialog_desc".txa(),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Select from file
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectFile() },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "txamusic_restore_from_file".txa(),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "txamusic_restore_from_file_desc".txa(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                if (existingBackups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "txamusic_restore_existing".txa(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Scrollable list for existing backups in Restore dialog
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            existingBackups.forEach { file -> 
                                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                val fileSize = TXAFormat.formatSize(file.length())
                                
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSelectExisting(file) },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${file.nameWithoutExtension.removePrefix("TXA_")} ($fileSize)",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = dateFormat.format(Date(file.lastModified())),
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("txamusic_btn_cancel".txa())
                }
            }
        }
    }
}

@Composable
fun BackupProgressDialog(
    progress: Int,
    message: String,
    isBackup: Boolean
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isBackup) "txamusic_backup_in_progress".txa() else "txamusic_restore_in_progress".txa(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "$progress%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun BackupResultDialog(
    success: Boolean,
    message: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (success) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.errorContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (success) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (success) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (success) "txamusic_backup_success_title".txa() else "txamusic_backup_failed_title".txa(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("txamusic_btn_ok".txa())
                }
            }
        }
    }
}

