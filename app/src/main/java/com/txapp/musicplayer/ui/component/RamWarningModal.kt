package com.txapp.musicplayer.ui.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.txapp.musicplayer.util.TXADeviceInfo
import com.txapp.musicplayer.util.TXAFormat
import com.txapp.musicplayer.util.txa
import androidx.compose.material.icons.filled.CleaningServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RamWarningModal(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Realtime RAM tracking
    var availableRam by remember { mutableLongStateOf(TXADeviceInfo.getAvailableRam()) }
    val totalRam = remember { TXADeviceInfo.getTotalRam() }
    
    // Auto-update RAM info every second
    LaunchedEffect(Unit) {
        while(true) {
            delay(1000)
            availableRam = TXADeviceInfo.getAvailableRam()
        }
    }

    Dialog(
        onDismissRequest = { /* Cannot dismiss until cleaned */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
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
                    text = "⚠️",
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "txamusic_ram_warning_title".txa(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Warning body
                val formattedTotal = TXAFormat.formatSize(totalRam)
                val minRam = "1.5 GB"
                Text(
                    text = "txamusic_ram_warning_body".txa(minRam, formattedTotal),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Realtime RAM Status
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "txamusic_ram_status".txa(
                                TXAFormat.formatSize(availableRam),
                                TXAFormat.formatSize(totalRam)
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        LinearProgressIndicator(
                            progress = { (totalRam - availableRam).toFloat() / totalRam.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(8.dp),
                            color = if (availableRam < 300 * 1024 * 1024) Color.Red else MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Clean RAM Button
                Button(
                    onClick = {
                        scope.launch {
                            val result = TXADeviceInfo.cleanAppMemory()
                            // Re-fetch after clean
                            delay(500) 
                            val newAvailable = TXADeviceInfo.getAvailableRam()
                            availableRam = newAvailable
                            
                            if (result.success) {
                                val freedText = if (result.freedBytes > 0) 
                                    TXAFormat.formatSize(result.freedBytes) 
                                else 
                                    "0 B"
                                val msg = "txamusic_ram_cleaned".txa(
                                    TXAFormat.formatSize(newAvailable),
                                    freedText
                                )
                                com.txapp.musicplayer.util.TXAToast.success(context, msg)
                            } else {
                                com.txapp.musicplayer.util.TXAToast.error(context, "txamusic_ram_clean_fail".txa("System Limit"))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "txamusic_action_clean_ram".txa())
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // Continue Button (Still allow user to try, but warn them)
                OutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "txamusic_action_continue".txa().uppercase(),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
