package com.spiritbyte.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Same 8x8 Bayer thresholds, facets and luminance calculation as SplashFox.tsx.
private val bayer = arrayOf(
    intArrayOf(0,32,8,40,2,34,10,42), intArrayOf(48,16,56,24,50,18,58,26),
    intArrayOf(12,44,4,36,14,46,6,38), intArrayOf(60,28,52,20,62,30,54,22),
    intArrayOf(3,35,11,43,1,33,9,41), intArrayOf(51,19,59,27,49,17,57,25),
    intArrayOf(15,47,7,39,13,45,5,37), intArrayOf(63,31,55,23,61,29,53,21)
)

internal fun foxArtwork(context: Context, primary: Color, accent: Color): ImageBitmap {
    val source = Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888)
    val drawable = requireNotNull(context.getDrawable(R.drawable.fox_facets))
    drawable.setBounds(30, 30, 390, 390)
    drawable.draw(android.graphics.Canvas(source))
    val pixels = IntArray(420 * 420)
    source.getPixels(pixels, 0, 420, 0, 0, 420, 420)
    val output = Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = android.graphics.Paint()
    for (y in 0 until 420 step 2) for (x in 0 until 420 step 2) {
        var intensity = 0f; var peak = 0f
        for (dy in 0..1) for (dx in 0..1) {
            val pixel = pixels[(y + dy) * 420 + x + dx]
            val value = (android.graphics.Color.red(pixel) * .299f + android.graphics.Color.green(pixel) * .587f +
                android.graphics.Color.blue(pixel) * .114f) / 255f * android.graphics.Color.alpha(pixel) / 255f
            intensity += value; peak = maxOf(peak, value)
        }
        intensity /= 4f
        if (intensity > .03f && intensity > bayer[(y / 2) and 7][(x / 2) and 7] / 64f * .85f) {
            paint.color = (if (peak > .78f) accent.copy(alpha = .95f) else primary.copy(alpha = .35f + intensity * .55f)).toArgb()
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 2f, y + 2f, paint)
        }
    }
    source.recycle()
    return output.asImageBitmap()
}

@Composable
fun FoxIntro(onDone: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val palette = LocalDesktopPalette.current
    val settings = LocalAppearance.current
    val finished by rememberUpdatedState(onDone)
    val reveal = remember { Animatable(0f) }
    val opacity = remember { Animatable(1f) }
    var bootIndex by remember { mutableIntStateOf(0) }
    val artwork by produceState<ImageBitmap?>(null, palette.id) {
        value = withContext(Dispatchers.Default) { runCatching { foxArtwork(context, palette.primary, palette.accent) }.getOrNull() }
    }
    val lines = listOf("SPIRITBYTE / ANDROID", "NÚCLEO LOCAL ...... LISTO", "CIFRADO ........... ARGON2id", "BÓVEDA ............ PRIVADA", "PHOSPHOR FOX ...... ONLINE")
    LaunchedEffect(Unit) {
        if (!ValueAnimator.areAnimatorsEnabled()) { reveal.snapTo(1f); bootIndex = lines.size; delay(600); finished() }
        else {
            launch { repeat(lines.size) { delay(420); bootIndex = it + 1 } }
            reveal.animateTo(1f, tween(2080, easing = LinearEasing))
            delay(670)
            opacity.animateTo(0f, tween(450))
            finished()
        }
    }
    BackHandler { finished() }
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.bg)) {
        val foxSize = minOf(maxWidth - 40.dp, maxHeight * .44f, 380.dp)
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp).alpha(opacity.value),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(foxSize)) {
                scale(size.width / 420f, size.height / 420f, pivot = Offset.Zero) {
                    listOf(8.6f to 66f, 98.5f to 72f, 189f to 69f, 279.6f to 70f).forEach { (start, sweep) ->
                        drawArc(palette.primary.copy(alpha = .16f), start, sweep, false, Offset(20f,20f), Size(380f,380f), style = Stroke(1f))
                    }
                    listOf(Offset(37f,37f), Offset(383f,37f), Offset(37f,383f), Offset(383f,383f)).forEach { p ->
                        val dx = if (p.x < 210) 15 else -15
                        val dy = if (p.y < 210) 15 else -15
                        drawLine(palette.primary.copy(alpha = .4f), p, p + Offset(dx.toFloat(),0f), 1f)
                        drawLine(palette.primary.copy(alpha = .4f), p, p + Offset(0f,dy.toFloat()), 1f)
                    }
                    clipRect(0f, 0f, 420f, 420f * reveal.value) {
                        artwork?.let { image ->
                            if (settings.glow) {
                                listOf(IntOffset(-2,0), IntOffset(2,0), IntOffset(0,-2), IntOffset(0,2)).forEach { offset ->
                                    drawImage(image, dstOffset = offset, dstSize = IntSize(420,420), alpha = .12f, filterQuality = FilterQuality.None)
                                }
                            }
                            drawImage(image, dstSize = IntSize(420,420), filterQuality = FilterQuality.None)
                        }
                    }
                    if (reveal.value < 1f) drawLine(
                        Brush.horizontalGradient(listOf(Color.Transparent, palette.accent, Color.Transparent), 35f,385f),
                        Offset(35f,420f * reveal.value), Offset(385f,420f * reveal.value), 1.5f)
                }
            }
            Text("SPIRITBYTE", style = MaterialTheme.typography.headlineLarge, color = palette.primary)
            Text("Tus secretos. Tu universo.", color = palette.dim)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().height(130.dp)) {
                lines.take(bootIndex).forEach { Text("> $it", style = MaterialTheme.typography.bodySmall, color = palette.accent) }
            }
            TextButton(onClick = { finished() }) { Text("Omitir intro") }
        }
    }
}
