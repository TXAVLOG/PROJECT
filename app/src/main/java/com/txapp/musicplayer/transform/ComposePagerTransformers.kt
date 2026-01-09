/*
 * Compose Page Transformers for HorizontalPager
 * These are equivalent animations to ViewPager.PageTransformer for Compose
 */

package com.txapp.musicplayer.transform

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/**
 * Enum for different page transformer styles
 */
enum class PagerTransformStyle {
    NORMAL,         // Scale effect
    CASCADING,      // Cascade with rotation
    DEPTH,          // Fade + scale depth effect
    HORIZONTAL_FLIP,// 3D flip horizontal
    VERTICAL_FLIP,  // 3D flip vertical  
    HINGE,          // Door hinge effect
    VERTICAL_STACK, // Stack cards effect
    CAROUSEL,       // Carousel effect
    DEFAULT         // No effect
}

/**
 * Extension function to apply page transformations to a Compose Modifier
 * @param pageOffset The offset of the page from the current position (-1..1)
 * @param style The transformation style to apply
 */
fun Modifier.pagerTransform(
    pageOffset: Float,
    style: PagerTransformStyle = PagerTransformStyle.NORMAL
): Modifier = this.then(
    when (style) {
        PagerTransformStyle.NORMAL -> normalTransform(pageOffset)
        PagerTransformStyle.CASCADING -> cascadingTransform(pageOffset)
        PagerTransformStyle.DEPTH -> depthTransform(pageOffset)
        PagerTransformStyle.HORIZONTAL_FLIP -> horizontalFlipTransform(pageOffset)
        PagerTransformStyle.VERTICAL_FLIP -> verticalFlipTransform(pageOffset)
        PagerTransformStyle.HINGE -> hingeTransform(pageOffset)
        PagerTransformStyle.VERTICAL_STACK -> verticalStackTransform(pageOffset)
        PagerTransformStyle.CAROUSEL -> carouselTransform(pageOffset)
        PagerTransformStyle.DEFAULT -> Modifier
    }
)

/**
 * Normal zoom in/out animation (equivalent to NormalPageTransformer)
 */
private fun normalTransform(pageOffset: Float): Modifier {
    val minScale = 0.85f
    val scaleFactor = maxOf(minScale, 1f - abs(pageOffset))
    
    return Modifier.graphicsLayer {
        scaleX = scaleFactor
        scaleY = scaleFactor
        
        // Adjust translation to center the scaled page
        val pageWidth = size.width
        val pageHeight = size.height
        val horzMargin = pageWidth * (1 - scaleFactor) / 2
        val vertMargin = pageHeight * (1 - scaleFactor) / 2
        
        translationX = if (pageOffset < 0) {
            horzMargin - vertMargin / 2
        } else {
            -horzMargin + vertMargin / 2
        }
    }
}

/**
 * Cascading effect with rotation (equivalent to CascadingPageTransformer)
 */
private fun cascadingTransform(pageOffset: Float): Modifier {
    val scaleOffset = 40f
    
    return Modifier.graphicsLayer {
        when {
            pageOffset < -1 -> {
                alpha = 0f
            }
            pageOffset <= 0 -> {
                alpha = 1f
                rotationZ = 45f * pageOffset
                translationX = size.width / 3 * pageOffset
            }
            else -> {
                alpha = 1f
                rotationZ = 0f
                val scale = (size.width - scaleOffset * pageOffset) / size.width
                scaleX = scale
                scaleY = scale
                translationX = -size.width * pageOffset
                translationY = scaleOffset * 0.8f * pageOffset
            }
        }
    }
}

/**
 * Depth effect with fade and scale (equivalent to DepthTransformation)
 */
private fun depthTransform(pageOffset: Float): Modifier {
    val minScale = 0.5f
    
    return Modifier.graphicsLayer {
        when {
            pageOffset < -1 -> {
                alpha = 0f
            }
            pageOffset <= 0 -> {
                alpha = 1f
                translationX = 0f
                scaleX = 1f
                scaleY = 1f
            }
            pageOffset <= 1 -> {
                // Fade the page out
                alpha = 1f - pageOffset
                // Counteract the default slide
                translationX = size.width * -pageOffset
                // Scale the page down
                val scaleFactor = minScale + (1 - minScale) * (1 - abs(pageOffset))
                scaleX = scaleFactor
                scaleY = scaleFactor
            }
            else -> {
                alpha = 0f
            }
        }
    }
}

/**
 * Horizontal flip 3D effect (equivalent to HorizontalFlipTransformation)
 */
private fun horizontalFlipTransform(pageOffset: Float): Modifier {
    return Modifier.graphicsLayer {
        translationX = -pageOffset * size.width
        cameraDistance = 20000f
        
        alpha = if (pageOffset < 0.5f && pageOffset > -0.5f) 1f else 0f
        
        when {
            pageOffset < -1 -> {
                alpha = 0f
            }
            pageOffset <= 0 -> {
                alpha = 1f
                rotationX = 180f * (1 - abs(pageOffset) + 1)
            }
            pageOffset <= 1 -> {
                alpha = 1f
                rotationX = -180f * (1 - abs(pageOffset) + 1)
            }
            else -> {
                alpha = 0f
            }
        }
    }
}

/**
 * Vertical flip 3D effect (equivalent to VerticalFlipTransformation)
 */
private fun verticalFlipTransform(pageOffset: Float): Modifier {
    return Modifier.graphicsLayer {
        translationX = -pageOffset * size.width
        cameraDistance = 100000f
        
        alpha = if (pageOffset < 0.5f && pageOffset > -0.5f) 1f else 0f
        
        when {
            pageOffset < -1 -> {
                alpha = 0f
            }
            pageOffset <= 0 -> {
                alpha = 1f
                rotationY = 180f * (1 - abs(pageOffset) + 1)
            }
            pageOffset <= 1 -> {
                alpha = 1f
                rotationY = -180f * (1 - abs(pageOffset) + 1)
            }
            else -> {
                alpha = 0f
            }
        }
    }
}

/**
 * Hinge door effect (equivalent to HingeTransformation)
 */
private fun hingeTransform(pageOffset: Float): Modifier {
    return Modifier.graphicsLayer {
        translationX = -pageOffset * size.width
        transformOrigin = TransformOrigin(0f, 0f)
        
        when {
            pageOffset < -1 -> {
                alpha = 0f
            }
            pageOffset <= 0 -> {
                rotationZ = 90f * abs(pageOffset)
                alpha = 1f - abs(pageOffset)
            }
            pageOffset <= 1 -> {
                rotationZ = 0f
                alpha = 1f
            }
            else -> {
                alpha = 0f
            }
        }
    }
}

/**
 * Vertical stack effect (equivalent to VerticalStackTransformer)
 */
private fun verticalStackTransform(pageOffset: Float): Modifier {
    return Modifier.graphicsLayer {
        if (pageOffset >= 0) {
            scaleX = 0.9f - 0.05f * pageOffset
            scaleY = 0.9f
            translationX = -size.width * pageOffset
            translationY = -30f * pageOffset
        }
    }
}

/**
 * Carousel effect (equivalent to CarousalPagerTransformer)
 */
private fun carouselTransform(pageOffset: Float): Modifier {
    val offsetRate = pageOffset * 0.30f
    val scaleFactor = 1f - abs(offsetRate)
    
    return Modifier.graphicsLayer {
        if (scaleFactor > 0) {
            scaleX = scaleFactor
            scaleY = scaleFactor
            // translationX is handled by HorizontalPager offset
        }
    }
}
