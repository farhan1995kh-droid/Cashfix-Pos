package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.i18n.AppLang
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandCyanBright
import com.example.ui.theme.BrandIceGlow
import com.example.ui.theme.BrandIceWhite
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandNavyDeep
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppOpeningMotionScreen(
    lang: AppLang,
    onFinished: () -> Unit
) {
    val waveProgress = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.55f) }
    val logoAlpha = remember { Animatable(0f) }
    val rippleProgress = remember { Animatable(0f) }
    val sheenProgress = remember { Animatable(-0.6f) }
    val progressFill = remember { Animatable(0f) }
    var showTypography by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    val finishOnce = remember(onFinished) {
        {
            if (!dismissed) {
                dismissed = true
                onFinished()
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "opening_pixels")
    val pixelDrift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pixel_drift"
    )

    LaunchedEffect(Unit) {
        launch {
            waveProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(140)
            launch {
                logoAlpha.animateTo(1f, animationSpec = tween(durationMillis = 360))
            }
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.60f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            delay(300)
            rippleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(480)
            showTypography = true
            sheenProgress.animateTo(
                targetValue = 1.6f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(250)
            progressFill.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1850, easing = FastOutSlowInEasing)
            )
        }
        delay(2250)
        finishOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color.White, BrandIceWhite, BrandIceGlow)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = finishOnce
            )
            .testTag("app_opening_motion_screen")
    ) {
        // Animated Theme Waves & Floating Pixel Squares
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val wp = waveProgress.value

            // Top-Left Outer Deep Navy Wave Ribbon (matches CashFix POS icon theme)
            val topNavyPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(w * 0.62f * wp, 0f)
                cubicTo(
                    w * 0.34f * wp, h * 0.08f * wp,
                    w * 0.14f * wp, h * 0.19f * wp,
                    0f, h * 0.42f * wp
                )
                close()
            }
            drawPath(
                path = topNavyPath,
                brush = Brush.linearGradient(
                    colors = listOf(BrandNavyDeep, BrandNavy),
                    start = Offset.Zero,
                    end = Offset(w * 0.45f, h * 0.35f)
                )
            )

            // Top-Left Inner Electric Cyan Swoosh Ribbon
            val topCyanPath = Path().apply {
                moveTo(w * 0.62f * wp, 0f)
                lineTo(w * 0.74f * wp, 0f)
                cubicTo(
                    w * 0.44f * wp, h * 0.12f * wp,
                    w * 0.22f * wp, h * 0.24f * wp,
                    0f, h * 0.50f * wp
                )
                lineTo(0f, h * 0.42f * wp)
                cubicTo(
                    w * 0.14f * wp, h * 0.19f * wp,
                    w * 0.34f * wp, h * 0.08f * wp,
                    w * 0.62f * wp, 0f
                )
                close()
            }
            drawPath(
                path = topCyanPath,
                brush = Brush.linearGradient(
                    colors = listOf(BrandCyan, BrandCyanBright.copy(alpha = 0.75f)),
                    start = Offset(0f, h * 0.45f),
                    end = Offset(w * 0.7f, 0f)
                )
            )

            // Bottom-Right Outer Deep Navy Wave Ribbon
            val botNavyPath = Path().apply {
                moveTo(w, h)
                lineTo(w - w * 0.62f * wp, h)
                cubicTo(
                    w - w * 0.34f * wp, h - h * 0.08f * wp,
                    w - w * 0.14f * wp, h - h * 0.19f * wp,
                    w, h - h * 0.42f * wp
                )
                close()
            }
            drawPath(
                path = botNavyPath,
                brush = Brush.linearGradient(
                    colors = listOf(BrandNavyDeep, BrandNavy),
                    start = Offset(w, h),
                    end = Offset(w * 0.55f, h * 0.65f)
                )
            )

            // Bottom-Right Inner Electric Cyan Swoosh Ribbon
            val botCyanPath = Path().apply {
                moveTo(w - w * 0.62f * wp, h)
                lineTo(w - w * 0.74f * wp, h)
                cubicTo(
                    w - w * 0.44f * wp, h - h * 0.12f * wp,
                    w - w * 0.22f * wp, h - h * 0.24f * wp,
                    w, h - h * 0.50f * wp
                )
                lineTo(w, h - h * 0.42f * wp)
                cubicTo(
                    w - w * 0.14f * wp, h - h * 0.19f * wp,
                    w - w * 0.34f * wp, h - h * 0.08f * wp,
                    w - w * 0.62f * wp, h
                )
                close()
            }
            drawPath(
                path = botCyanPath,
                brush = Brush.linearGradient(
                    colors = listOf(BrandCyan, BrandCyanBright.copy(alpha = 0.75f)),
                    start = Offset(w * 0.3f, h),
                    end = Offset(w, h * 0.55f)
                )
            )

            // Expanding Cyan Pulse Ripple Ring around Logo Center
            val rp = rippleProgress.value
            if (rp > 0f && rp < 1f) {
                val maxRadius = minOf(w, h) * 0.36f
                val currentRadius = maxRadius * (0.45f + 0.55f * rp)
                drawCircle(
                    color = BrandCyan.copy(alpha = (1f - rp) * 0.38f),
                    radius = currentRadius,
                    center = center,
                    style = Stroke(width = (10f * (1f - rp)).coerceAtLeast(2f))
                )
            }

            // Floating Digital Pixel Squares (echoing the CashFix POS "x" pixel sparks)
            val pixelSpecs = listOf(
                Triple(0.66f, 0.33f, 18f),
                Triple(0.72f, 0.28f, 14f),
                Triple(0.77f, 0.34f, 11f),
                Triple(0.69f, 0.23f, 15f),
                Triple(0.27f, 0.66f, 12f),
                Triple(0.22f, 0.71f, 9f)
            )
            pixelSpecs.forEachIndexed { idx, (fx, fy, boxSize) ->
                val phase = (pixelDrift + idx * 0.16f) % 1f
                val alpha = kotlin.math.sin(phase * Math.PI).toFloat().coerceIn(0f, 1f) * wp
                val px = w * fx + phase * 18f
                val py = h * fy - phase * 42f
                val color = if (idx % 2 == 0) BrandCyan else BrandNavyDeep
                rotate(degrees = 12f + phase * 25f, pivot = Offset(px, py)) {
                    drawRoundRect(
                        color = color.copy(alpha = alpha * 0.85f),
                        topLeft = Offset(px - boxSize / 2f, py - boxSize / 2f),
                        size = Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
        }

        // Center Brand Emblem Card + Motion Sheen + Animated Typography
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(38.dp),
                    color = Color.White,
                    shadowElevation = 18.dp,
                    border = BorderStroke(2.dp, BrandCyan.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.cashfix_brand_logo),
                            contentDescription = "CashFix POS",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(36.dp)),
                            contentScale = ContentScale.Crop
                        )

                        // Light Sweep Sheen Overlay
                        val sp = sheenProgress.value
                        if (sp in -0.5f..1.5f) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val startX = size.width * sp
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.55f),
                                            Color.Transparent
                                        ),
                                        start = Offset(startX - 90f, 0f),
                                        end = Offset(startX + 90f, size.height)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showTypography,
                enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 3 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = BrandNavyDeep
                    ) {
                        Text(
                            text = if (lang == AppLang.AR) "نظام نقاط البيع الذكي" else "SMART POINT OF SALE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    Text(
                        text = if (lang == AppLang.AR) {
                            "البيع السريع • الفوترة الإلكترونية ZATCA • قائمة QR"
                        } else {
                            "Fast Billing • ZATCA QR • Self-Ordering"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandNavy.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Sleek Gradient Progress Track
            Box(
                modifier = Modifier
                    .width(196.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(BrandNavy.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFill.value.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(BrandNavyDeep, BrandCyan)
                            )
                        )
                )
            }
        }

        // Subtle Skip Pill in Top-Right Safe Area
        Surface(
            onClick = finishOnce,
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.86f),
            border = BorderStroke(1.dp, BrandNavy.copy(alpha = 0.18f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .testTag("skip_opening_motion_btn")
        ) {
            Text(
                text = if (lang == AppLang.AR) "تخطي" else "Skip",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = BrandNavyDeep,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}
