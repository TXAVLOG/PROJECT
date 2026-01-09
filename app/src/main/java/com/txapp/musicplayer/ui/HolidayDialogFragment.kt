package com.txapp.musicplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import com.txapp.musicplayer.util.TXAFireworks
import com.txapp.musicplayer.util.TXAHolidayManager
import com.txapp.musicplayer.util.txa
import nl.dionsegijn.konfetti.compose.KonfettiView

class HolidayDialogFragment : DialogFragment() {

    private var eventType: TXAHolidayManager.HolidayEventType? = null

    companion object {
        fun newInstance(type: TXAHolidayManager.HolidayEventType): HolidayDialogFragment {
            val f = HolidayDialogFragment()
            f.eventType = type
            return f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    HolidayScreen(eventType = eventType, onDismiss = { 
                        dismiss() 
                    })
                }
            }
        }
    }
}

@Composable
fun HolidayScreen(eventType: TXAHolidayManager.HolidayEventType?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val message = remember(eventType) { getGreetingMessage(eventType) }
    var dontShowAgain by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        // Fireworks
        KonfettiView(
            modifier = Modifier.fillMaxSize(),
            parties = remember { TXAFireworks.explode() }
        )

        // Content Card
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉",
                    fontSize = 64.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = message.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it }
                    )
                    Text(
                        text = "txamusic_holiday_dont_show_today".txa(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Button(
                    onClick = {
                        if (dontShowAgain && eventType != null) {
                            TXAHolidayManager.markAsShown(context, eventType)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("txamusic_action_continue".txa())
                }
            }
        }
    }
}

data class HolidayMessage(val title: String, val body: String)

fun getGreetingMessage(type: TXAHolidayManager.HolidayEventType?): HolidayMessage {
    return when (type) {
        TXAHolidayManager.HolidayEventType.NEW_YEAR_SOLAR -> HolidayMessage(
            "txamusic_holiday_newyear_title".txa(),
            "txamusic_holiday_newyear_body".txa()
        )
        TXAHolidayManager.HolidayEventType.TET_27 -> HolidayMessage(
            "txamusic_holiday_tet_27_title".txa(),
            "txamusic_holiday_tet_27_body".txa()
        )
        TXAHolidayManager.HolidayEventType.TET_28 -> HolidayMessage(
            "txamusic_holiday_tet_28_title".txa(),
            "txamusic_holiday_tet_28_body".txa()
        )
        TXAHolidayManager.HolidayEventType.TET_29 -> HolidayMessage(
            "txamusic_holiday_tet_29_title".txa(),
            "txamusic_holiday_tet_29_body".txa()
        )
        TXAHolidayManager.HolidayEventType.TET_30 -> HolidayMessage(
             "txamusic_holiday_tatnien_title".txa(),
             "txamusic_holiday_tatnien_body".txa()
        )
        TXAHolidayManager.HolidayEventType.GIAO_THUA -> HolidayMessage(
             "txamusic_holiday_giaothua_title".txa(),
             "txamusic_holiday_giaothua_body".txa()
        )
        TXAHolidayManager.HolidayEventType.TET_MUNG_1 -> HolidayMessage(
             "txamusic_holiday_mung1_title".txa(),
             "txamusic_holiday_mung1_body".txa()
        )
        TXAHolidayManager.HolidayEventType.TET_MUNG_1_EXTRA -> HolidayMessage(
             "txamusic_holiday_mung1_extra_title".txa(),
             "txamusic_holiday_mung1_extra_body".txa()
        )
        TXAHolidayManager.HolidayEventType.TET_MUNG_2 -> HolidayMessage(
             "txamusic_holiday_mung2_title".txa(),
             "txamusic_holiday_mung2_body".txa()
        )
        TXAHolidayManager.HolidayEventType.TET_MUNG_3 -> HolidayMessage(
             "txamusic_holiday_mung3_title".txa(),
             "txamusic_holiday_mung3_body".txa()
        )
        TXAHolidayManager.HolidayEventType.TET_MUNG_4 -> HolidayMessage(
             "txamusic_holiday_mung4_title".txa(),
             "txamusic_holiday_mung4_body".txa()
        )
        else -> HolidayMessage("Happy Holidays!", "Wishing you joy and happiness!")
    }
}
