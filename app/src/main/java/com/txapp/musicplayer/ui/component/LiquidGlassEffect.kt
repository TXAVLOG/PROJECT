package com.txapp.musicplayer.ui.component

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Liquid Glass Effect using AGSL (Android Graphics Shading Language)
 * 
 * Requirements:
 * - Android 13 (API 33) or higher for RuntimeShader
 * - Jetpack Compose for UI integration
 * 
 * This provides a frosted glass effect with subtle refraction and specular highlights,
 * inspired by Apple's Liquid Glass and Google's Material You glassmorphism.
 */

// AGSL Shader Code for Liquid Glass Effect
private const val LIQUID_GLASS_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform float blurAmount;
    uniform float refractionStrength;
    uniform float4 tintColor;
    uniform float4 highlightColor;
    uniform float isDark;
    uniform shader contents;
    
    // Simple noise function for subtle distortion
    float noise(float2 p) {
        return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
    }
    
    // Smooth noise
    float smoothNoise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        
        float a = noise(i);
        float b = noise(i + float2(1.0, 0.0));
        float c = noise(i + float2(0.0, 1.0));
        float d = noise(i + float2(1.0, 1.0));
        
        return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
    }
    
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        
        // Create subtle wave distortion for liquid effect
        float distortion = smoothNoise(uv * 10.0 + time * 0.5) * refractionStrength;
        float2 distortedUV = fragCoord + float2(
            sin(uv.y * 15.0 + time) * distortion,
            cos(uv.x * 15.0 + time) * distortion
        );
        
        // Sample the background with distortion
        half4 color = contents.eval(distortedUV);
        
        // Apply blur simulation through color averaging
        half4 blurColor = (
            contents.eval(distortedUV + float2(blurAmount, 0)) +
            contents.eval(distortedUV - float2(blurAmount, 0)) +
            contents.eval(distortedUV + float2(0, blurAmount)) +
            contents.eval(distortedUV - float2(0, blurAmount))
        ) / 4.0;
        
        color = mix(color, blurColor, 0.7);
        
        // Apply tint overlay
        color = mix(color, half4(tintColor), 0.3);
        
        // Add specular highlight at top edge (simulates glass reflection)
        float highlight = smoothstep(0.0, 0.15, uv.y) * (1.0 - smoothstep(0.0, 0.3, uv.y));
        highlight *= 0.5 + 0.5 * sin(uv.x * 20.0 + time * 2.0) * 0.1;
        color += half4(highlightColor) * highlight * 0.4;
        
        // Edge glow for glass border feel
        float edge = 1.0 - smoothstep(0.0, 0.02, uv.x) * 
                     smoothstep(0.0, 0.02, 1.0 - uv.x) *
                     smoothstep(0.0, 0.05, uv.y) * 
                     smoothstep(0.0, 0.05, 1.0 - uv.y);
        color += half4(highlightColor) * edge * 0.15;
        
        // Adjust alpha for transparency
        float baseAlpha = isDark > 0.5 ? 0.75 : 0.65;
        color.a = baseAlpha;
        
        return color;
    }
"""

/**
 * Check if Liquid Glass effect is supported on this device
 */
fun isLiquidGlassSupported(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU // API 33
}

/**
 * Creates a Liquid Glass modifier for Compose elements
 * Only applies effect on Android 13+, returns unmodified for older versions
 */
@Composable
fun Modifier.liquidGlass(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    tintColor: Color = if (isDarkTheme) Color(0xFF1A1A2E) else Color(0xFFF5F5F5),
    blurAmount: Float = 8f,
    refractionStrength: Float = 3f,
    animateWaves: Boolean = true
): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.then(
            Modifier.liquidGlassInternal(
                isDarkTheme = isDarkTheme,
                tintColor = tintColor,
                blurAmount = blurAmount,
                refractionStrength = refractionStrength,
                animateWaves = animateWaves
            )
        )
    } else {
        // Fallback: just return the modifier without effect
        this
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.liquidGlassInternal(
    isDarkTheme: Boolean,
    tintColor: Color,
    blurAmount: Float,
    refractionStrength: Float,
    animateWaves: Boolean
): Modifier {
    var time by remember { mutableFloatStateOf(0f) }
    
    // Animate time for wave effect
    if (animateWaves) {
        LaunchedEffect(Unit) {
            while (true) {
                time += 0.016f // ~60fps
                kotlinx.coroutines.delay(16)
            }
        }
    }
    
    val highlightColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFFFFFFFF)
    
    return this.graphicsLayer {
        val shader = RuntimeShader(LIQUID_GLASS_SHADER).apply {
            setFloatUniform("resolution", size.width, size.height)
            setFloatUniform("time", if (animateWaves) time else 0f)
            setFloatUniform("blurAmount", blurAmount)
            setFloatUniform("refractionStrength", refractionStrength)
            setFloatUniform("tintColor", 
                tintColor.red, tintColor.green, tintColor.blue, tintColor.alpha)
            setFloatUniform("highlightColor",
                highlightColor.red, highlightColor.green, highlightColor.blue, highlightColor.alpha)
            setFloatUniform("isDark", if (isDarkTheme) 1f else 0f)
        }
        
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "contents")
            .asComposeRenderEffect()
    }
}

/**
 * Simpler glass effect using RenderEffect blur for broader compatibility
 * Works on Android 12+ (API 31)
 */
@Composable
fun Modifier.simpleGlassEffect(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    blurRadius: Float = 25f
): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31
        this.graphicsLayer {
            val blurEffect = RenderEffect.createBlurEffect(
                blurRadius, blurRadius,
                android.graphics.Shader.TileMode.CLAMP
            )
            renderEffect = blurEffect.asComposeRenderEffect()
        }
    } else {
        this
    }
}

/**
 * Object to hold Liquid Glass theme colors
 */
object LiquidGlassColors {
    @Composable
    fun surfaceColor(isDark: Boolean = isSystemInDarkTheme()): Color {
        return if (isDark) {
            Color(0xFF1A1A2E).copy(alpha = 0.7f)
        } else {
            Color(0xFFF8F8FC).copy(alpha = 0.75f)
        }
    }
    
    @Composable
    fun borderColor(isDark: Boolean = isSystemInDarkTheme()): Color {
        return if (isDark) {
            Color.White.copy(alpha = 0.15f)
        } else {
            Color.White.copy(alpha = 0.5f)
        }
    }
    
    @Composable
    fun contentColor(isDark: Boolean = isSystemInDarkTheme()): Color {
        return if (isDark) Color.White else Color(0xFF1A1A2E)
    }
    
    @Composable
    fun secondaryContentColor(isDark: Boolean = isSystemInDarkTheme()): Color {
        return if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF1A1A2E).copy(alpha = 0.7f)
    }
}
