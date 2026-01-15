package com.txapp.musicplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.txapp.musicplayer.util.TXAFilePickerUtil
import com.txapp.musicplayer.util.txa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TXACustomFilePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onFileSelected: (String) -> Unit,
    allowedExtensions: Set<String>? = null
) {
    // Start at External Storage Directory
    var currentPath by remember { mutableStateOf(android.os.Environment.getExternalStorageDirectory().absolutePath) }
    var fileList by remember { mutableStateOf(emptyList<TXAFilePickerUtil.FileInfo>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentPath) {
        isLoading = true
        fileList = TXAFilePickerUtil.getFiles(currentPath, allowedExtensions)
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // Full screen
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                TopAppBar(
                    title = {
                        var showMenu by remember { mutableStateOf(false) }
                        val hasRoot by remember { mutableStateOf(com.txapp.musicplayer.util.TXASuHelper.getRootStatus() == true || com.txapp.musicplayer.util.TXASuHelper.isRooted()) }
                        
                        Box {
                            Column(
                                modifier = Modifier
                                    .clickable { showMenu = true }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(currentPath, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("txamusic_internal_storage".txa()) },
                                    onClick = {
                                        showMenu = false
                                        currentPath = android.os.Environment.getExternalStorageDirectory().absolutePath
                                    },
                                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) }
                                )
                                
                                if (hasRoot) {
                                    DropdownMenuItem(
                                        text = { Text("txamusic_storage_root".txa()) },
                                        onClick = {
                                            showMenu = false
                                            currentPath = "/"
                                        },
                                        leadingIcon = { Icon(Icons.Default.DeveloperBoard, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            val parent = File(currentPath).parentFile
                            if (parent != null && parent.canRead()) {
                                currentPath = parent.absolutePath
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (fileList.isEmpty()) {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("txamusic_empty_folder".txa(), color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(fileList) { info ->
                            SimpleFileItem(
                                info = info,
                                onClick = {
                                    if (info.isDirectory) {
                                        currentPath = info.file.absolutePath
                                    } else {
                                        onFileSelected(info.file.absolutePath)
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleFileItem(
    info: TXAFilePickerUtil.FileInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Icon(
            imageVector = if (info.isDirectory) Icons.Default.Folder else if (info.isAudio) Icons.Default.MusicNote else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (info.isDirectory) MaterialTheme.colorScheme.secondary else if (info.isAudio) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(32.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Details: Date | Size | Duration
                val details = buildString {
                    append(info.dateModifiedStr)
                    append(" • ")
                    append(info.sizeStr)
                    if (info.durationStr != null) {
                        append(" • ")
                        append(info.durationStr)
                    }
                }
                Text(
                    text = details,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
