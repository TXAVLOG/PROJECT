package com.txapp.musicplayer.ui.component

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.txapp.musicplayer.util.TXAPermissionHelper
import com.txapp.musicplayer.util.txa

@Composable
fun PermissionDialog(
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var storageGranted by remember { mutableStateOf(TXAPermissionHelper.hasAllFilesPermission(context)) }
    var notificationGranted by remember { mutableStateOf(TXAPermissionHelper.hasNotificationPermission(context)) }
    var writeSettingsGranted by remember { mutableStateOf(TXAPermissionHelper.hasWriteSettingsPermission(context)) }
    var locationGranted by remember { mutableStateOf(TXAPermissionHelper.hasLocationPermission(context)) }
    var exactAlarmGranted by remember { mutableStateOf(TXAPermissionHelper.hasExactAlarmPermission(context)) }
    var systemAlertWindowGranted by remember { mutableStateOf(TXAPermissionHelper.hasSystemAlertWindowPermission(context)) }

    // Check permissions when app resumes (e.g. user returns from Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                storageGranted = TXAPermissionHelper.hasAllFilesPermission(context)
                notificationGranted = TXAPermissionHelper.hasNotificationPermission(context)
                writeSettingsGranted = TXAPermissionHelper.hasWriteSettingsPermission(context)
                locationGranted = TXAPermissionHelper.hasLocationPermission(context)
                exactAlarmGranted = TXAPermissionHelper.hasExactAlarmPermission(context)
                systemAlertWindowGranted = TXAPermissionHelper.hasSystemAlertWindowPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Polling check to ensure UI updates even if ON_RESUME doesn't fire perfectly
    LaunchedEffect(Unit) {
        while(true) {
            storageGranted = TXAPermissionHelper.hasAllFilesPermission(context)
            notificationGranted = TXAPermissionHelper.hasNotificationPermission(context)
            writeSettingsGranted = TXAPermissionHelper.hasWriteSettingsPermission(context)
            locationGranted = TXAPermissionHelper.hasLocationPermission(context)
            exactAlarmGranted = TXAPermissionHelper.hasExactAlarmPermission(context)
            systemAlertWindowGranted = TXAPermissionHelper.hasSystemAlertWindowPermission(context)
            kotlinx.coroutines.delay(500)
        }
    }
    
    // Required permissions: Storage, Location, and Write Settings (for Ringtone/Volume)
    // Optional: Notification (Android 13+), Exact Alarm (Android 12+)
    val requiredGranted = storageGranted && locationGranted && writeSettingsGranted

    Dialog(
        onDismissRequest = {}, // Non-dismissable
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Text(
                    text = "txamusic_permissions_title".txa(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "txamusic_permissions_desc".txa(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    var itemNumber = 1

                    // ========================
                    // REQUIRED PERMISSIONS
                    // ========================
                    
                    // 1. Storage Permission Item (REQUIRED)
                    PermissionItem(
                        number = itemNumber++,
                        title = "txamusic_permission_storage_title".txa(),
                        subtitle = "txamusic_permission_storage_message".txa(),
                        icon = Icons.Default.Folder,
                        isGranted = storageGranted,
                        isOptional = false,
                        onGrant = {
                            TXAPermissionHelper.requestAllFilesPermission(context, 101)
                        },
                        onCheck = {
                            storageGranted = TXAPermissionHelper.hasAllFilesPermission(context)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 2. Location Permission Item (REQUIRED)
                    PermissionItem(
                        number = itemNumber++,
                        title = "txamusic_permission_location_title".txa(),
                        subtitle = "txamusic_permission_location_required_desc".txa(),
                        icon = Icons.Default.Place,
                        isGranted = locationGranted,
                        isOptional = false,
                        onGrant = {
                            TXAPermissionHelper.requestLocationPermission(context, 103)
                        },
                        onCheck = {
                            locationGranted = TXAPermissionHelper.hasLocationPermission(context)
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. Write Settings Permission (REQUIRED)
                    PermissionItem(
                        number = itemNumber++,
                        title = "txamusic_permission_write_settings_title".txa(),
                        subtitle = "txamusic_permission_write_settings_desc".txa(),
                        icon = Icons.Default.Settings,
                        isGranted = writeSettingsGranted,
                        isOptional = false,
                        onGrant = {
                            TXAPermissionHelper.requestWriteSettingsPermission(context)
                        },
                        onCheck = {
                            writeSettingsGranted = TXAPermissionHelper.hasWriteSettingsPermission(context)
                        }
                    )

                    // ========================
                    // OPTIONAL PERMISSIONS
                    // ========================
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Section divider for optional permissions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "txamusic_permission_optional".txa(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 3. Notification Permission (OPTIONAL, Android 13+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionItem(
                            number = itemNumber++,
                            title = "txamusic_permission_notification_title".txa(),
                            subtitle = "txamusic_permission_notification_desc".txa(),
                            icon = Icons.Default.Notifications,
                            isGranted = notificationGranted,
                            isOptional = true,
                            onGrant = {
                                TXAPermissionHelper.requestNotificationPermission(context, 102)
                            },
                            onCheck = {
                                notificationGranted = TXAPermissionHelper.hasNotificationPermission(context)
                            }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 5. Exact Alarm Permission (OPTIONAL, Android 12+)
                    // On older versions, this is automatically granted so we don't show it
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PermissionItem(
                            number = itemNumber++,
                            title = "txamusic_permission_alarm_title".txa(),
                            subtitle = "txamusic_permission_alarm_desc".txa(),
                            icon = Icons.Default.Alarm,
                            isGranted = exactAlarmGranted,
                            isOptional = true,
                            onGrant = {
                                TXAPermissionHelper.requestExactAlarmPermission(context)
                            },
                            onCheck = {
                                exactAlarmGranted = TXAPermissionHelper.hasExactAlarmPermission(context)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 6. System Alert Window (OPTIONAL)
                    PermissionItem(
                        number = itemNumber++,
                        title = "txamusic_permission_system_alert_title".txa(),
                        subtitle = "txamusic_permission_system_alert_desc".txa(),
                        icon = androidx.compose.material.icons.Icons.Outlined.Lyrics,
                        isGranted = systemAlertWindowGranted,
                        isOptional = true,
                        onGrant = {
                            TXAPermissionHelper.requestSystemAlertWindowPermission(context)
                        },
                        onCheck = {
                            systemAlertWindowGranted = TXAPermissionHelper.hasSystemAlertWindowPermission(context)
                        }
                    )
                }
                
                Button(
                    onClick = {
                        // Re-check all required permissions
                        storageGranted = TXAPermissionHelper.hasAllFilesPermission(context)
                        locationGranted = TXAPermissionHelper.hasLocationPermission(context)
                        writeSettingsGranted = TXAPermissionHelper.hasWriteSettingsPermission(context)
                        
                        // Only require essential permissions (Storage, Location, Write Settings)
                        val required = storageGranted && locationGranted && writeSettingsGranted
                        if (required) {
                            onAllGranted()
                        }
                    },
                    enabled = requiredGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "txamusic_btn_confirm".txa(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    number: Int,
    title: String,
    subtitle: String,
    icon: ImageVector,
    isGranted: Boolean,
    isOptional: Boolean = false,
    onGrant: () -> Unit,
    onCheck: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Number Badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    brush = if (isOptional) {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF9E9E9E), Color(0xFF757575))
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                        )
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isOptional) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "txamusic_permission_optional".txa(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "txamusic_permission_status_granted".txa(),
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { 
                        onGrant()
                        // Delay check slightly to allow system dialog to appear/process
                        // In reality, actual check happens onResume
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("txamusic_btn_grant".txa())
                }
            }
        }
    }
}
