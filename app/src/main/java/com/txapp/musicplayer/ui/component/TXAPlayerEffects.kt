package com.txapp.musicplayer.ui.component

import android.media.audiofx.Visualizer
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.txapp.musicplayer.service.MusicService
import com.txapp.musicplayer.util.TXAPreferences
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Enum các loại hiệu ứng có sẵn cho trình phát
 */
enum class PlayerEffectType(val key: String, val displayName: String) {
    NONE("none", "Off"),
    SNOW("snow", "Snow ❄️"),
    STARS("stars", "Stars ⭐"),
    BUBBLES("bubbles", "Bubbles 🫧"),
    SAKURA("sakura", "Sakura 🌸"),
    FIREFLIES("fireflies", "Fireflies ✨"),
    RAIN("rain", "Rain 🌧️"),
    CONFETTI("confetti", "Confetti 🎊"),
    HEARTS("hearts", "Hearts ❤️");
    
    companion object {
        fun fromKey(key: String): PlayerEffectType {
            return entries.firstOrNull { it.key == key } ?: NONE
        }
    }
}

/**
 * Wrapper composable để hiển thị hiệu ứng cho trình phát
 */
@Composable
fun TXAPlayerEffectsOverlay(
    modifier: Modifier = Modifier,
    effectType: PlayerEffectType = PlayerEffectType.NONE,
    particleCount: Int = 50
) {
    val isEnabled by TXAPreferences.playerEffectsEnabled.collectAsState()
    
    if (!isEnabled || effectType == PlayerEffectType.NONE) {
        return
    }
    
    // Audio visualization data
    val audioLevel = rememberAudioVisualizerLevel()
    
    Box(modifier = modifier.fillMaxSize()) {
        when (effectType) {
            PlayerEffectType.SNOW -> SnowEffect(particleCount)
            PlayerEffectType.STARS -> StarsEffect(particleCount)
            PlayerEffectType.BUBBLES -> BubblesEffectReactive(particleCount, audioLevel)
            PlayerEffectType.SAKURA -> SakuraEffectReactive(particleCount, audioLevel)
            PlayerEffectType.FIREFLIES -> FirefliesKonfettiEffect()
            PlayerEffectType.RAIN -> RainEffect(particleCount * 2)
            PlayerEffectType.CONFETTI -> ConfettiEffect(particleCount)
            PlayerEffectType.HEARTS -> HeartsEffectReactive(particleCount, audioLevel)
            else -> {}
        }
    }
}

/**
 * Audio Visualizer để lấy mức độ âm thanh theo nhịp nhạc
 */
@Composable
private fun rememberAudioVisualizerLevel(): Float {
    val sessionId = MusicService.audioSessionId
    var audioLevel by remember(sessionId) { mutableFloatStateOf(0.5f) }
    
    DisposableEffect(sessionId) {
        var visualizer: Visualizer? = null
        
        if (sessionId > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                visualizer = Visualizer(sessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[0]
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            waveform?.let { data ->
                                // Calculate average amplitude
                                var sum = 0f
                                for (byte in data) {
                                    sum += kotlin.math.abs(byte.toInt() - 128)
                                }
                                val avg = sum / data.size
                                audioLevel = (avg / 128f).coerceIn(0f, 1f)
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            // Not used
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, true, false)
                    enabled = true
                }
            } catch (e: Exception) {
                // Visualizer not available, use fallback
                audioLevel = 0.5f
            }
        }
        
        onDispose {
            try {
                visualizer?.enabled = false
                visualizer?.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    return audioLevel
}

// ==================== SNOW EFFECT ====================
private data class Snowflake(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float,
    val wobble: Float,
    var phase: Float
)

@Composable
private fun SnowEffect(count: Int) {
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }
    
    // Divide snow into 3 layers for depth
    val snowflakes = remember(count) {
        mutableStateListOf<Snowflake>().apply {
            repeat(count) { 
                add(createSnowflake(screenWidth, screenHeight, false)) 
            }
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "snow")
    val windSway by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wind"
    )
    
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    LaunchedEffect(time) {
        snowflakes.forEachIndexed { index, flake ->
            val newY = flake.y + flake.speed
            // Wobble + Wind sway effect
            val wobbleX = sin((flake.phase + time * 0.05f).toDouble()).toFloat() * (flake.wobble * 0.5f)
            val totalX = flake.x + wobbleX * 0.2f + windSway * (flake.speed * 0.5f)
            
            if (newY > screenHeight + 20) {
                snowflakes[index] = createSnowflake(screenWidth, screenHeight, true)
            } else {
                snowflakes[index] = flake.copy(
                    x = totalX,
                    y = newY,
                    phase = flake.phase + 0.1f
                )
            }
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        screenWidth = size.width
        screenHeight = size.height
        
        snowflakes.forEach { flake ->
            // Simulating depth of field with opacity and size
            // Smaller = further away = lower opacity
            val depthAlpha = (flake.size / 6f).coerceIn(0.2f, 0.8f) * flake.alpha
            
            // Draw a soft glowy snowflake
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = depthAlpha),
                    1.0f to Color.Transparent,
                    center = Offset(flake.x, flake.y),
                    radius = flake.size * 1.5f
                ),
                radius = flake.size * 1.5f,
                center = Offset(flake.x, flake.y)
            )
            
            // Solid core for closer snowflakes
            if (flake.size > 4f) {
                drawCircle(
                    color = Color.White.copy(alpha = depthAlpha * 0.8f),
                    radius = flake.size * 0.6f,
                    center = Offset(flake.x, flake.y)
                )
            }
        }
    }
}

private fun createSnowflake(w: Float, h: Float, fromTop: Boolean): Snowflake {
    // Parallax logic: larger particles are "closer", so they are larger and move faster
    val sizeBase = Random.nextFloat()
    val size = sizeBase * 5f + 1f // 1 to 6
    val speed = (sizeBase * 2f + 1f) * 1.2f // Faster if larger
    
    return Snowflake(
        x = Random.nextFloat() * w,
        y = if (fromTop) -Random.nextFloat() * 100 else Random.nextFloat() * h,
        size = size,
        speed = speed,
        alpha = Random.nextFloat() * 0.5f + 0.3f,
        wobble = Random.nextFloat() * 15f + 5f,
        phase = Random.nextFloat() * 360f
    )
}

// ==================== STARS EFFECT ====================
private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float,
    var twinklePhase: Float
)

@Composable
private fun StarsEffect(count: Int) {
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }
    
    val stars = remember(count) {
        mutableStateListOf<Star>().apply {
            repeat(count) { add(createStar(screenWidth, screenHeight)) }
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "twinkle"
    )
    
    LaunchedEffect(time) {
        stars.forEachIndexed { index, star ->
            stars[index] = star.copy(twinklePhase = star.twinklePhase + 0.1f)
        }
    }
    
    val shootingStarAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3000
                0f at 0
                0f at 1000
                1f at 1200
                1f at 1400
                0f at 1600
                0f at 3000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "shooting"
    )
    
    val shootingStarProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shootingPos"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        screenWidth = size.width
        screenHeight = size.height
        
        stars.forEach { star ->
            val twinkle = (sin(star.twinklePhase.toDouble()).toFloat() + 1f) / 2f
            val alpha = star.alpha * (0.2f + twinkle * 0.8f)
            
            // Soft glow
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color(0xFFFFE082).copy(alpha = alpha),
                    1.0f to Color.Transparent,
                    center = Offset(star.x, star.y),
                    radius = star.size * 3f
                ),
                radius = star.size * 3f,
                center = Offset(star.x, star.y)
            )
            
            // Core
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = star.size,
                center = Offset(star.x, star.y)
            )
        }
        
        // Shooting star effect
        if (shootingStarAlpha > 0f) {
            val startX = screenWidth * 0.8f
            val startY = screenHeight * 0.2f
            val endX = screenWidth * 0.2f
            val endY = screenHeight * 0.5f
            
            val currentX = startX + (endX - startX) * shootingStarProgress
            val currentY = startY + (endY - startY) * shootingStarProgress
            
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = shootingStarAlpha)),
                    start = Offset(currentX + 50f, currentY - 30f),
                    end = Offset(currentX, currentY)
                ),
                start = Offset(currentX + 100f, currentY - 60f),
                end = Offset(currentX, currentY),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

private fun createStar(w: Float, h: Float) = Star(
    x = Random.nextFloat() * w,
    y = Random.nextFloat() * h,
    size = Random.nextFloat() * 3f + 1f,
    alpha = Random.nextFloat() * 0.5f + 0.5f,
    twinklePhase = Random.nextFloat() * 360f
)

// ==================== BUBBLES EFFECT (REACTIVE) ====================
private data class Bubble(
    var x: Float,
    var y: Float,
    var size: Float,
    val baseSize: Float,
    val speed: Float,
    val alpha: Float,
    val wobble: Float,
    var phase: Float
)

@Composable
private fun BubblesEffectReactive(count: Int, audioLevel: Float) {
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }
    
    val bubbles = remember(count) {
        mutableStateListOf<Bubble>().apply {
            repeat(count) { add(createBubbleReactive(screenWidth, screenHeight, false)) }
        }
    }
    
    val time by rememberInfiniteTransition(label = "bubbles").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    LaunchedEffect(time, audioLevel) {
        bubbles.forEachIndexed { index, bubble ->
            val newY = bubble.y - bubble.speed * (1f + audioLevel * 0.5f)
            val wobbleX = sin((bubble.phase + time * 0.01f).toDouble()).toFloat() * bubble.wobble
            
            // Size pulsates with audio
            val pulsedSize = bubble.baseSize * (1f + audioLevel * 0.5f)
            
            if (newY < -50) {
                bubbles[index] = createBubbleReactive(screenWidth, screenHeight, true)
            } else {
                bubbles[index] = bubble.copy(
                    x = bubble.x + wobbleX * 0.05f,
                    y = newY,
                    size = pulsedSize,
                    phase = bubble.phase + 0.05f
                )
            }
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        screenWidth = size.width
        screenHeight = size.height
        
        bubbles.forEach { bubble ->
            drawCircle(
                color = Color(0xFF87CEEB).copy(alpha = bubble.alpha * 0.4f),
                radius = bubble.size,
                center = Offset(bubble.x, bubble.y)
            )
            drawCircle(
                color = Color.White.copy(alpha = bubble.alpha * 0.6f),
                radius = bubble.size * 0.3f,
                center = Offset(bubble.x - bubble.size * 0.3f, bubble.y - bubble.size * 0.3f)
            )
        }
    }
}

private fun createBubbleReactive(w: Float, h: Float, fromBottom: Boolean): Bubble {
    val baseSize = Random.nextFloat() * 20f + 10f
    return Bubble(
        x = Random.nextFloat() * w,
        y = if (fromBottom) h + Random.nextFloat() * 100 else Random.nextFloat() * h,
        size = baseSize,
        baseSize = baseSize,
        speed = Random.nextFloat() * 1.5f + 0.5f,
        alpha = Random.nextFloat() * 0.5f + 0.3f,
        wobble = Random.nextFloat() * 30f + 10f,
        phase = Random.nextFloat() * 360f
    )
}

// ==================== SAKURA EFFECT (REACTIVE) ====================
private data class SakuraPetal(
    var x: Float,
    var y: Float,
    var size: Float,
    val baseSize: Float,
    val speed: Float,
    val rotation: Float,
    var phase: Float,
    val color: Color
)

@Composable
private fun SakuraEffectReactive(count: Int, audioLevel: Float) {
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }
    
    val colors = listOf(
        Color(0xFFFFB7C5),
        Color(0xFFFFDAD6),
        Color(0xFFFF69B4),
        Color(0xFFFFC0CB)
    )
    
    val petals = remember(count) {
        mutableStateListOf<SakuraPetal>().apply {
            repeat(count) { add(createSakuraReactive(screenWidth, screenHeight, colors, false)) }
        }
    }
    
    val time by rememberInfiniteTransition(label = "sakura").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    LaunchedEffect(time, audioLevel) {
        petals.forEachIndexed { index, petal ->
            val speedMultiplier = 1f + audioLevel * 0.8f
            val newY = petal.y + petal.speed * speedMultiplier
            val sway = sin((petal.phase + time * 0.02f).toDouble()).toFloat() * 25f * (1f + audioLevel * 0.5f)
            val pulsedSize = petal.baseSize * (1f + audioLevel * 0.3f)
            
            if (newY > screenHeight + 50) {
                petals[index] = createSakuraReactive(screenWidth, screenHeight, colors, true)
            } else {
                petals[index] = petal.copy(
                    x = petal.x + sway * 0.05f,
                    y = newY,
                    size = pulsedSize,
                    phase = petal.phase + 0.08f * speedMultiplier
                )
            }
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        screenWidth = size.width
        screenHeight = size.height
        
        petals.forEach { petal ->
            rotate(degrees = petal.rotation + petal.phase * 2f, pivot = Offset(petal.x, petal.y)) {
                val path = Path().apply {
                    moveTo(petal.x, petal.y - petal.size)
                    cubicTo(
                        petal.x + petal.size * 0.6f, petal.y - petal.size * 0.5f,
                        petal.x + petal.size * 0.6f, petal.y + petal.size * 0.5f,
                        petal.x, petal.y + petal.size * 0.7f
                    )
                    cubicTo(
                        petal.x - petal.size * 0.6f, petal.y + petal.size * 0.5f,
                        petal.x - petal.size * 0.6f, petal.y - petal.size * 0.5f,
                        petal.x, petal.y - petal.size
                    )
                    close()
                }
                drawPath(path = path, color = petal.color.copy(alpha = 0.8f))
            }
        }
    }
}

private fun createSakuraReactive(w: Float, h: Float, colors: List<Color>, fromTop: Boolean): SakuraPetal {
    val baseSize = Random.nextFloat() * 12f + 8f
    return SakuraPetal(
        x = Random.nextFloat() * w,
        y = if (fromTop) -Random.nextFloat() * 150 else Random.nextFloat() * h,
        size = baseSize,
        baseSize = baseSize,
        speed = Random.nextFloat() * 1.5f + 0.8f,
        rotation = Random.nextFloat() * 360f,
        phase = Random.nextFloat() * 360f,
        color = colors.random()
    )
}

// ==================== FIREFLIES EFFECT (KONFETTI) ====================
@Composable
private fun FirefliesKonfettiEffect() {
    val fireflyColors = listOf(0xFFFF00, 0xFFFFE0, 0xFFD700, 0x90EE90)
    
    val parties = remember {
        listOf(
            // Sparkles floating around
            Party(
                speed = 2f,
                maxSpeed = 8f,
                damping = 0.95f,
                angle = 270,
                spread = 360,
                colors = fireflyColors,
                shapes = listOf(Shape.Circle),
                size = listOf(Size(4), Size(6), Size(3)),
                position = Position.Relative(0.5, 0.5).between(Position.Relative(0.0, 0.0)),
                emitter = Emitter(duration = Long.MAX_VALUE, TimeUnit.MILLISECONDS).perSecond(15)
            ),
            // Random bursts
            Party(
                speed = 5f,
                maxSpeed = 15f,
                damping = 0.9f,
                angle = 270,
                spread = 360,
                colors = fireflyColors,
                shapes = listOf(Shape.Circle),
                size = listOf(Size(5)),
                position = Position.Relative(0.3, 0.3),
                emitter = Emitter(duration = 3, TimeUnit.SECONDS).perSecond(10)
            ),
            Party(
                speed = 5f,
                maxSpeed = 15f,
                damping = 0.9f,
                angle = 270,
                spread = 360,
                colors = fireflyColors,
                shapes = listOf(Shape.Circle),
                size = listOf(Size(5)),
                position = Position.Relative(0.7, 0.6),
                emitter = Emitter(duration = 3, TimeUnit.SECONDS).perSecond(10)
            )
        )
    }
    
    KonfettiView(
        modifier = Modifier.fillMaxSize(),
        parties = parties
    )
}

// ==================== RAIN EFFECT ====================
private data class Raindrop(
    var x: Float,
    var y: Float,
    val length: Float,
    val speed: Float,
    val alpha: Float,
    val angle: Float = 5f // Slight angle for wind effect
)

@Composable
private fun RainEffect(count: Int) {
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }
    
    val raindrops = remember(count) {
        mutableStateListOf<Raindrop>().apply {
            repeat(count) { add(createRaindrop(screenWidth, screenHeight, false)) }
        }
    }
    
    val time by rememberInfiniteTransition(label = "rain").animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    LaunchedEffect(time) {
        raindrops.forEachIndexed { index, drop ->
            val newY = drop.y + drop.speed
            val newX = drop.x + drop.angle * 0.5f // Wind drift
            
            if (newY > screenHeight) {
                raindrops[index] = createRaindrop(screenWidth, screenHeight, true)
            } else {
                raindrops[index] = drop.copy(x = newX, y = newY)
            }
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        screenWidth = size.width
        screenHeight = size.height
        
        raindrops.forEach { drop ->
            // Draw rain streak
            drawLine(
                color = Color(0xFF6BB3F9).copy(alpha = drop.alpha),
                start = Offset(drop.x, drop.y),
                end = Offset(drop.x + drop.angle, drop.y + drop.length),
                strokeWidth = 1.5f
            )
            // Subtle splash at current position
            if (Random.nextFloat() > 0.95f) {
                drawCircle(
                    color = Color(0xFF6BB3F9).copy(alpha = drop.alpha * 0.3f),
                    radius = 3f,
                    center = Offset(drop.x, drop.y + drop.length)
                )
            }
        }
    }
}

private fun createRaindrop(w: Float, h: Float, fromTop: Boolean) = Raindrop(
    x = Random.nextFloat() * w,
    y = if (fromTop) -Random.nextFloat() * 50 else Random.nextFloat() * h,
    length = Random.nextFloat() * 25f + 15f,
    speed = Random.nextFloat() * 20f + 15f,
    alpha = Random.nextFloat() * 0.4f + 0.3f,
    angle = Random.nextFloat() * 8f + 2f
)

// ==================== CONFETTI EFFECT ====================
private data class ConfettiPiece(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    var rotation: Float,
    val rotationSpeed: Float,
    val color: Color,
    val wobble: Float,
    var phase: Float
)

@Composable
private fun ConfettiEffect(count: Int) {
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }
    
    val colors = listOf(
        Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77),
        Color(0xFF4D96FF), Color(0xFFC9B1FF), Color(0xFFFF8CC8)
    )
    
    val confetti = remember(count) {
        mutableStateListOf<ConfettiPiece>().apply {
            repeat(count) { add(createConfetti(screenWidth, screenHeight, colors, false)) }
        }
    }
    
    val time by rememberInfiniteTransition(label = "confetti").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    LaunchedEffect(time) {
        confetti.forEachIndexed { index, piece ->
            val newY = piece.y + piece.speed
            val wobbleX = sin((piece.phase).toDouble()).toFloat() * piece.wobble
            
            if (newY > screenHeight + 30) {
                confetti[index] = createConfetti(screenWidth, screenHeight, colors, true)
            } else {
                confetti[index] = piece.copy(
                    x = piece.x + wobbleX * 0.1f,
                    y = newY,
                    rotation = piece.rotation + piece.rotationSpeed,
                    phase = piece.phase + 0.1f
                )
            }
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        screenWidth = size.width
        screenHeight = size.height
        
        confetti.forEach { piece ->
            rotate(degrees = piece.rotation, pivot = Offset(piece.x, piece.y)) {
                drawRect(
                    color = piece.color,
                    topLeft = Offset(piece.x - piece.size / 2, piece.y - piece.size / 4),
                    size = androidx.compose.ui.geometry.Size(piece.size, piece.size / 2)
                )
            }
        }
    }
}

private fun createConfetti(w: Float, h: Float, colors: List<Color>, fromTop: Boolean) = ConfettiPiece(
    x = Random.nextFloat() * w,
    y = if (fromTop) -Random.nextFloat() * 150 else Random.nextFloat() * h,
    size = Random.nextFloat() * 12f + 6f,
    speed = Random.nextFloat() * 2f + 1f,
    rotation = Random.nextFloat() * 360f,
    rotationSpeed = Random.nextFloat() * 6f - 3f,
    color = colors.random(),
    wobble = Random.nextFloat() * 30f + 15f,
    phase = Random.nextFloat() * 360f
)

// ==================== HEARTS EFFECT (REACTIVE) ====================
private data class Heart(
    var x: Float,
    var y: Float,
    var size: Float,
    val baseSize: Float,
    val speed: Float,
    var phase: Float,
    val color: Color
)

@Composable
private fun HeartsEffectReactive(count: Int, audioLevel: Float) {
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }
    
    val colors = listOf(
        Color(0xFFFF6B6B), Color(0xFFFF1493), Color(0xFFFF69B4), Color(0xFFDC143C)
    )
    
    val hearts = remember(count) {
        mutableStateListOf<Heart>().apply {
            repeat(count) { add(createHeartReactive(screenWidth, screenHeight, colors, false)) }
        }
    }
    
    val time by rememberInfiniteTransition(label = "hearts").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    LaunchedEffect(time, audioLevel) {
        hearts.forEachIndexed { index, heart ->
            val speedMultiplier = 1f + audioLevel * 0.6f
            val newY = heart.y - heart.speed * speedMultiplier
            val wobbleX = sin(heart.phase.toDouble()).toFloat() * 15f
            val pulsedSize = heart.baseSize * (1f + audioLevel * 0.4f)
            
            if (newY < -50) {
                hearts[index] = createHeartReactive(screenWidth, screenHeight, colors, true)
            } else {
                hearts[index] = heart.copy(
                    x = heart.x + wobbleX * 0.05f,
                    y = newY,
                    size = pulsedSize,
                    phase = heart.phase + 0.05f * speedMultiplier
                )
            }
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        screenWidth = size.width
        screenHeight = size.height
        
        hearts.forEach { heart ->
            drawHeart(heart.x, heart.y, heart.size, heart.color.copy(alpha = 0.7f))
        }
    }
}

private fun DrawScope.drawHeart(cx: Float, cy: Float, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(cx, cy + size * 0.3f)
        cubicTo(
            cx - size, cy - size * 0.3f,
            cx - size * 0.5f, cy - size,
            cx, cy - size * 0.5f
        )
        cubicTo(
            cx + size * 0.5f, cy - size,
            cx + size, cy - size * 0.3f,
            cx, cy + size * 0.3f
        )
        close()
    }
    drawPath(path = path, color = color)
}

private fun createHeartReactive(w: Float, h: Float, colors: List<Color>, fromBottom: Boolean): Heart {
    val baseSize = Random.nextFloat() * 15f + 10f
    return Heart(
        x = Random.nextFloat() * w,
        y = if (fromBottom) h + Random.nextFloat() * 100 else Random.nextFloat() * h,
        size = baseSize,
        baseSize = baseSize,
        speed = Random.nextFloat() * 1.5f + 0.5f,
        phase = Random.nextFloat() * 360f,
        color = colors.random()
    )
}
