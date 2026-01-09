package com.txapp.musicplayer.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

/**
 * Data class cho một cánh hoa rơi
 */
private data class Petal(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val swayAmplitude: Float,
    val swayFrequency: Float,
    val color: Color,
    val initialX: Float,
    var phase: Float = Random.nextFloat() * 360f
)

/**
 * Enum để chọn loại hoa ngẫu nhiên mỗi ngày
 */
enum class FlowerType {
    MAI,   // Hoa Mai vàng
    DAO    // Hoa Đào hồng
}

/**
 * Xác định loại hoa dựa trên ngày (random nhưng cố định trong ngày)
 */
private fun getFlowerTypeForToday(): FlowerType {
    val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    return if (dayOfYear % 2 == 0) FlowerType.MAI else FlowerType.DAO
}

/**
 * Lấy màu sắc cho cánh hoa dựa theo loại
 */
private fun getPetalColors(type: FlowerType): List<Color> {
    return when (type) {
        FlowerType.MAI -> listOf(
            Color(0xFFFFD700), // Vàng gold
            Color(0xFFFFC107), // Vàng amber
            Color(0xFFFFEB3B), // Vàng nhạt
            Color(0xFFFFA000)  // Vàng cam
        )
        FlowerType.DAO -> listOf(
            Color(0xFFFF69B4), // Hồng hot
            Color(0xFFFFB6C1), // Hồng nhạt
            Color(0xFFFF1493), // Hồng đậm
            Color(0xFFFFC0CB)  // Hồng phấn
        )
    }
}

/**
 * Composable hiệu ứng hoa mai/đào rơi như tuyết
 * Random loại hoa mỗi ngày (Mai hoặc Đào)
 */
@Composable
fun TXAFallingPetalsOverlay(
    modifier: Modifier = Modifier,
    petalCount: Int = 40
) {
    val flowerType = remember { getFlowerTypeForToday() }
    val colors = remember { getPetalColors(flowerType) }
    
    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }
    
    // State cho các cánh hoa
    val petals = remember(petalCount) {
        mutableStateListOf<Petal>().apply {
            repeat(petalCount) {
                add(createPetal(screenWidth, screenHeight, colors, startFromTop = false))
            }
        }
    }
    
    // Animation loop
    val infiniteTransition = rememberInfiniteTransition(label = "petals")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    // Cập nhật vị trí cánh hoa
    LaunchedEffect(time) {
        petals.forEachIndexed { index, petal ->
            // Di chuyển xuống
            val newY = petal.y + petal.speed
            
            // Dao động ngang (sway)
            val sway = sin((petal.phase + time * petal.swayFrequency).toDouble()).toFloat() * petal.swayAmplitude
            val newX = petal.initialX + sway
            
            // Cập nhật phase cho rotation
            petal.phase += petal.rotationSpeed
            
            if (newY > screenHeight + 50) {
                // Reset về trên cùng
                petals[index] = createPetal(screenWidth, screenHeight, colors, startFromTop = true)
            } else {
                petals[index] = petal.copy(x = newX, y = newY, phase = petal.phase)
            }
        }
    }
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
    ) {
        screenWidth = size.width
        screenHeight = size.height
        
        petals.forEach { petal ->
            drawPetal(petal)
        }
    }
}

private fun createPetal(
    screenWidth: Float,
    screenHeight: Float,
    colors: List<Color>,
    startFromTop: Boolean
): Petal {
    return Petal(
        x = Random.nextFloat() * screenWidth,
        y = if (startFromTop) -Random.nextFloat() * 200 else Random.nextFloat() * screenHeight,
        size = Random.nextFloat() * 12f + 8f, // 8-20dp
        speed = Random.nextFloat() * 1.5f + 0.5f, // 0.5-2 speed
        rotation = Random.nextFloat() * 360f,
        rotationSpeed = Random.nextFloat() * 2f - 1f, // -1 to 1
        swayAmplitude = Random.nextFloat() * 30f + 10f, // 10-40 pixels
        swayFrequency = Random.nextFloat() * 0.5f + 0.3f, // 0.3-0.8
        color = colors.random(),
        initialX = Random.nextFloat() * screenWidth
    )
}

private fun DrawScope.drawPetal(petal: Petal) {
    rotate(degrees = petal.rotation + petal.phase, pivot = Offset(petal.x, petal.y)) {
        // Vẽ hình cánh hoa (ellipse méo nhẹ)
        val path = Path().apply {
            // Cánh hoa hình giọt nước/ellipse
            moveTo(petal.x, petal.y - petal.size)
            // Đường cong bên phải
            cubicTo(
                petal.x + petal.size * 0.6f, petal.y - petal.size * 0.5f,
                petal.x + petal.size * 0.6f, petal.y + petal.size * 0.5f,
                petal.x, petal.y + petal.size * 0.7f
            )
            // Đường cong bên trái
            cubicTo(
                petal.x - petal.size * 0.6f, petal.y + petal.size * 0.5f,
                petal.x - petal.size * 0.6f, petal.y - petal.size * 0.5f,
                petal.x, petal.y - petal.size
            )
            close()
        }
        
        drawPath(
            path = path,
            color = petal.color.copy(alpha = 0.85f)
        )
    }
}
