package com.txapp.musicplayer.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.txapp.musicplayer.util.TXAHolidayManager
import com.txapp.musicplayer.util.TXALogger
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import java.util.concurrent.TimeUnit

/**
 * TXAHolidayBorderOverlay - Hiệu ứng lễ hội
 * - Tất Niên (27-30 tháng Chạp): Hoa mai/đào rơi như tuyết
 * - Normal (Mùng 1, Tết Dương): Pháo hoa Konfetti
 */
@Composable
fun TXAHolidayBorderOverlay(content: @Composable () -> Unit) {
    val holidayMode = TXAHolidayManager.getHolidayMode()
    val isEnabled by com.txapp.musicplayer.util.TXAPreferences.holidayEffectEnabled.collectAsState()
    val isActive = (holidayMode != TXAHolidayManager.HolidayMode.NONE) && isEnabled
    
    LaunchedEffect(holidayMode) {
        TXALogger.holidayI("TXAHoliday", "Holiday Overlay Mode: $holidayMode")
    }

    if (!isActive) {
        content()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. App Content - nhận mọi touch event bình thường
        content()
        
        // 2. Overlay dựa theo mode
        when (holidayMode) {
            TXAHolidayManager.HolidayMode.TAT_NIEN -> {
                // Hiệu ứng hoa mai/đào rơi như tuyết
                TXAFallingPetalsOverlay(
                    modifier = Modifier.fillMaxSize(),
                    petalCount = 50
                )
            }
            TXAHolidayManager.HolidayMode.NORMAL -> {
                // Pháo hoa Konfetti cho Mùng 1, Tết Dương
                KonfettiView(
                    modifier = Modifier.fillMaxSize(),
                    parties = rememberKonfettiParties()
                )
            }
            else -> { /* NONE - đã xử lý ở trên */ }
        }
    }
}

/**
 * Cấu hình Konfetti cho chế độ NORMAL (Mùng 1, Tết Dương)
 */
@Composable
private fun rememberKonfettiParties(): List<Party> {
    val festiveColors = listOf(0xFFD700, 0xFF6B6B, 0xE91E63, 0x9C27B0, 0x00BCD4, 0x4CAF50)
    
    return remember {
        listOf(
            // 1. Pháo hoa nổ từ giữa dưới lên (Opening Burst - One Shot)
            Party(
                speed = 30f,
                maxSpeed = 50f,
                damping = 0.9f,
                angle = 270,
                spread = 360,
                colors = festiveColors,
                shapes = listOf(Shape.Circle, Shape.Square),
                size = listOf(Size(8), Size(12), Size(16, 6f)),
                position = Position.Relative(0.5, 1.0),
                emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(50)
            ),
            // 2. Rain effect (Continuous - Subtle)
            Party(
                speed = 0f,
                maxSpeed = 15f,
                damping = 0.9f,
                angle = 270,
                spread = 90,
                colors = festiveColors,
                shapes = listOf(Shape.Circle),
                size = listOf(Size(4), Size(8)),
                position = Position.Relative(0.5, 0.0).between(Position.Relative(0.0, 0.0)),
                emitter = Emitter(duration = Long.MAX_VALUE, TimeUnit.MILLISECONDS).perSecond(20)
            ),
            // 3. Góc trái nổ (One Shot)
            Party(
                speed = 20f,
                maxSpeed = 40f,
                damping = 0.9f,
                angle = 315,
                spread = 60,
                colors = festiveColors,
                shapes = listOf(Shape.Circle, Shape.Square),
                size = listOf(Size(6), Size(10)),
                position = Position.Relative(0.0, 0.5),
                emitter = Emitter(duration = 2, TimeUnit.SECONDS).perSecond(20)
            ),
            // 4. Góc phải nổ (One Shot)
            Party(
                speed = 20f,
                maxSpeed = 40f,
                damping = 0.9f,
                angle = 225,
                spread = 60,
                colors = festiveColors,
                shapes = listOf(Shape.Circle, Shape.Square),
                size = listOf(Size(6), Size(10)),
                position = Position.Relative(1.0, 0.5),
                emitter = Emitter(duration = 2, TimeUnit.SECONDS).perSecond(20)
            ),
            // 5. Sparkles nhỏ khắp nơi (Continuous - Ambient)
            Party(
                speed = 5f,
                maxSpeed = 15f,
                damping = 0.8f,
                angle = 270,
                spread = 360,
                colors = listOf(0xFFFFFF, 0xFFD700, 0xFFF8DC),
                shapes = listOf(Shape.Circle),
                size = listOf(Size(3)),
                position = Position.Relative(0.5, 0.5).between(Position.Relative(0.0, 0.0)),
                emitter = Emitter(duration = Long.MAX_VALUE, TimeUnit.MILLISECONDS).perSecond(10)
            )
        )
    }
}

