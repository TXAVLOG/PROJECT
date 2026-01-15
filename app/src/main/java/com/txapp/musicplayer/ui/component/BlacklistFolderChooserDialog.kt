package com.txapp.musicplayer.ui.component

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.txapp.musicplayer.data.BlackListEntity
import java.io.File

/**
 * Dialog for choosing folders to blacklist
 * Prevents songs in selected folders from appearing in library
 */
@Composable
fun BlacklistFolderChooserDialog(
    currentBlacklist: List<BlackListEntity>,
    onDismiss: () -> Unit,
    onAddBlacklist: (String) -> Unit,
    onRemoveBlacklist: (String) -> Unit
) {
    var currentPath by remember { 
        mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) 
    }
    
    val folders by remember(currentPath) {
        derivedStateOf { getFoldersInPath(currentPath) }
    }
    
    val blacklistedPaths = remember(currentBlacklist) {
        currentBlacklist.map { it.path }.toSet()
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Blacklist Folders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                // Breadcrumb navigation
                BreadcrumbNavigation(
                    currentPath = currentPath,
                    onNavigate = { newPath -> currentPath = newPath }
                )
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // Folder list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // Parent directory option
                    if (currentPath != "/") {
                        item {
                            FolderItem(
                                name = "..",
                                path = File(currentPath).parent ?: "/",
                                isBlacklisted = false,
                                isParent = true,
                                onClick = { 
                                    currentPath = File(currentPath).parent ?: "/"
                                },
                                onToggleBlacklist = {}
                            )
                        }
                    }
                    
                    // Current directories
                    items(folders) { folder ->
                        val isBlacklisted = blacklistedPaths.contains(folder.absolutePath)
                        
                        FolderItem(
                            name = folder.name,
                            path = folder.absolutePath,
                            isBlacklisted = isBlacklisted,
                            isParent = false,
                            onClick = { 
                                currentPath = folder.absolutePath 
                            },
                            onToggleBlacklist = { path ->
                                if (isBlacklisted) {
                                    onRemoveBlacklist(path)
                                } else {
                                    onAddBlacklist(path)
                                }
                            }
                        )
                    }
                    
                    if (folders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No folders found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbNavigation(
    currentPath: String,
    onNavigate: (String) -> Unit
) {
    val pathSegments = remember(currentPath) {
        currentPath.split("/").filter { it.isNotEmpty() }
    }
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Root
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { onNavigate("/") },
                    label = { Text("Root") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                if (pathSegments.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Path segments
        items(pathSegments.size) { index ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val segment = pathSegments[index]
                val segmentPath = "/" + pathSegments.take(index + 1).joinToString("/")
                
                AssistChip(
                    onClick = { onNavigate(segmentPath) },
                    label = { Text(segment) }
                )
                
                if (index < pathSegments.size - 1) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderItem(
    name: String,
    path: String,
    isBlacklisted: Boolean,
    isParent: Boolean,
    onClick: () -> Unit,
    onToggleBlacklist: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isParent) Icons.Default.ArrowUpward else Icons.Default.Folder,
            contentDescription = null,
            tint = if (isBlacklisted) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(Modifier.width(16.dp))
        
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isBlacklisted) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        
        if (!isParent) {
            IconButton(
                onClick = { onToggleBlacklist(path) }
            ) {
                Icon(
                    imageVector = if (isBlacklisted) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.AddCircleOutline
                    },
                    contentDescription = if (isBlacklisted) "Remove from blacklist" else "Add to blacklist",
                    tint = if (isBlacklisted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Get all folders in the given path
 */
private fun getFoldersInPath(path: String): List<File> {
    return try {
        File(path)
            .listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
