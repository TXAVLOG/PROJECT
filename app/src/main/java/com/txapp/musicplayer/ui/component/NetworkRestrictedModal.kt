package com.txapp.musicplayer.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.txapp.musicplayer.util.txa

@Composable
fun NetworkRestrictedModal(
    onContinue: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismiss */ },
        title = { Text("txamusic_network_restricted_mode".txa()) },
        text = { Text("txamusic_network_restricted_mode_desc".txa()) },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text("txamusic_btn_confirm".txa())
            }
        },
        dismissButton = null
    )
}
