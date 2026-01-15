package com.txapp.musicplayer.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.txapp.musicplayer.util.txa

/**
 * Dialog for importing M3U playlist files
 */
@Composable
fun ImportPlaylistDialog(
    onDismiss: () -> Unit,
    onImport: (filePath: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val path = com.txapp.musicplayer.util.TXAFilePickerUtil.getPath(context, it)
            if (path != null && (path.endsWith(".m3u", ignoreCase = true) || 
                path.endsWith(".m3u8", ignoreCase = true))) {
                onImport(path)
                onDismiss()
            } else {
                 com.txapp.musicplayer.util.TXAToast.error(context, "Invalid playlist file")
            }
        }
    }
    
    var showCustomPicker by remember { mutableStateOf(false) }
    
    if (showCustomPicker) {
        TXACustomFilePickerDialog(
            title = "txamusic_select_file".txa(),
            onDismiss = { showCustomPicker = false },
            onFileSelected = { path ->
                onImport(path)
                showCustomPicker = false
                onDismiss()
            },
            allowedExtensions = setOf("m3u", "m3u8")
        )
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Title
                    Text(
                        text = "txamusic_import_playlist".txa(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Description
                    Text(
                        text = "txamusic_import_playlist_desc".txa(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("txamusic_btn_cancel".txa())
                        }
                        
                        Button(
                            onClick = { filePicker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("System Picker")
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    TextButton(
                        onClick = { showCustomPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Verify Storage")
                    }
                }
            }
        }
    }
}
