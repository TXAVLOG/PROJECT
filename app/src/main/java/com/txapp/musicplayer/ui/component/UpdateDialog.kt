package com.txapp.musicplayer.ui.component

// cms

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import com.txapp.musicplayer.util.DownloadState
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.TXAUpdateManager
import com.txapp.musicplayer.util.TXATranslation
import com.txapp.musicplayer.util.txa
import com.txapp.musicplayer.util.UpdateInfo
import java.io.File

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    downloadState: DownloadState?,
    resolving: Boolean,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
    onInstallClick: (File) -> Unit
) {
    val context = LocalContext.current
    
    // Animation state for smooth entrance
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100) // Small delay for smoother entrance
        showContent = true
    }

    Dialog(
        onDismissRequest = if (updateInfo.forceUpdate) ({}) else onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Animate the entire dialog content for smooth appearance
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 300,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) + slideInVertically(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 300,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                ),
                initialOffsetY = { it / 4 }
            ) + scaleIn(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 300,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                ),
                initialScale = 0.9f
            ),
            exit = fadeOut(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Text(
                    text = "txamusic_update_available".txa().format(updateInfo.latestVersionName),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "txamusic_update_date".txa().format(TXAFormat.formatUtcToLocal(updateInfo.releaseDate)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Nội dung chính
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. Luôn hiện Changelog (trừ khi đã tải xong hoặc lỗi quá nghiêm trọng)
                    if (downloadState !is DownloadState.Success && downloadState !is DownloadState.Error) {
                        Text(
                            text = "txamusic_update_whats_new".txa(),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Box(
                            modifier = Modifier
                                .height(if (downloadState == null && !resolving) 220.dp else 140.dp)
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(4.dp) // Padding ngoài để lộ border/background
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent, // Để WebView quản lý màu nền
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                AndroidView(
                                    modifier = Modifier.padding(8.dp),
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            setBackgroundColor(0)
                                            loadDataWithBaseURL(null, updateInfo.changelog, "text/html", "UTF-8", null)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // 2. Trạng thái Đang Resolve link
                    if (resolving) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("txamusic_update_resolving".txa(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // 3. Trạng thái Đang tải
                    if (downloadState is DownloadState.Progress) {
                        Spacer(modifier = Modifier.height(16.dp))
                        DownloadProgressUI(downloadState)
                    }

                    // 4. Trạng thái Lỗi
                    if (downloadState is DownloadState.Error) {
                        ErrorAndCopyUI(url = updateInfo.downloadUrl, errorMessage = downloadState.message)
                    }

                    // 5. Trạng thái Thành công
                    if (downloadState is DownloadState.Success) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("txamusic_update_ready".txa(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Dòng nút bấm (Cập nhật logic ẩn hiện)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    // Nút "Để sau" (Ẩn khi đang tải hoặc bắt buộc update)
                    if (!updateInfo.forceUpdate && downloadState !is DownloadState.Progress && downloadState !is DownloadState.Success && !resolving) {
                        TextButton(onClick = onDismiss) { Text("txamusic_btn_later".txa()) }
                    }

                    // Nút "Hủy tải" (Chỉ hiện khi đang tải)
                    if (downloadState is DownloadState.Progress || resolving) {
                        TextButton(
                            onClick = { TXAUpdateManager.stopDownload(context) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("txamusic_btn_cancel_download".txa())
                        }
                    }
                    
                    // Nút chức năng chính
                    when (downloadState) {
                        is DownloadState.Success -> {
                            Button(
                                onClick = { onInstallClick(downloadState.file) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Icon(Icons.Default.InstallMobile, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("txamusic_btn_install".txa())
                            }
                        }
                        is DownloadState.Progress -> {
                            // Không hiện nút khi đang tải để tránh bấm nhầm
                        }
                        is DownloadState.Error -> {
                            TextButton(onClick = onDismiss) { Text("txamusic_btn_confirm".txa()) }
                        }
                        else -> {
                            if (!resolving) {
                                Button(
                                    onClick = onUpdateClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Download, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("txamusic_btn_update".txa())
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun DownloadProgressUI(state: DownloadState.Progress) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("txamusic_update_downloading".txa(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(TXAFormat.formatSpeed(state.bps), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { state.percentage / 100f },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${TXAFormat.formatBytes(state.downloaded)} / ${TXAFormat.formatBytes(state.total)}", style = MaterialTheme.typography.labelSmall)
            Text("ETA: ${TXAFormat.formatETA(state.total - state.downloaded, state.bps)}", style = MaterialTheme.typography.labelSmall)
            Text("${state.percentage}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun ErrorAndCopyUI(url: String, errorMessage: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copyStatus by remember { mutableStateOf<Boolean?>(null) }
    
    LaunchedEffect(copyStatus) { if (copyStatus != null) { delay(2000); copyStatus = null } }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text("txamusic_update_failed".txa(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        Text(errorMessage, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                try {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(i)
                } catch (e: Exception) {
                    com.txapp.musicplayer.util.TXAToast.error(context, "txamusic_browser_not_found".txa())
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) 
        ) {
            Icon(Icons.Default.OpenInBrowser, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("txamusic_btn_browser".txa())
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("txamusic_update_copy_hint".txa(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().clickable { 
                try { clipboard.setText(AnnotatedString(url)); copyStatus = true } catch (e: Exception) { copyStatus = false }
            },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = url, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(8.dp))
                AnimatedContent(targetState = copyStatus) { status ->
                    when (status) {
                        true -> Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        false -> Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        else -> Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
