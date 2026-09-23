package com.spiritbyte.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.widget.ImageView
import java.io.File
import java.nio.ByteBuffer

/** Native decoding, with no image-loading framework or Compose updates per frame. */
internal fun decodeAnimatedWallpaper(file: File): Drawable {
    if (Build.VERSION.SDK_INT >= 28) {
        // Own the encoded data: the imported temporary file is deleted after decoding.
        return ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(file.readBytes()))) { decoder, info, _ ->
            val scale = maxOf(1f, maxOf(info.size.width, info.size.height) / 960f)
            decoder.setTargetSize((info.size.width / scale).toInt().coerceAtLeast(1),
                (info.size.height / scale).toInt().coerceAtLeast(1))
        }.also { if (it is AnimatedImageDrawable) it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE }
    }
    // Movie cannot subsample on Android 8; bound dimensions before allocating frames.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    require(bounds.outWidth in 1..960 && bounds.outHeight in 1..960) {
        "En Android 8, el GIF debe medir como máximo 960 × 960 píxeles."
    }
    @Suppress("DEPRECATION")
    val movie = file.inputStream().use { Movie.decodeStream(it) } ?: error("No se pudo abrir el GIF.")
    return MovieWallpaper(movie)
}

@Suppress("DEPRECATION")
private class MovieWallpaper(private val movie: Movie) : Drawable(), Animatable, Runnable {
    private var playing = false
    private var started = 0L
    private var elapsed = 0L
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    override fun getIntrinsicWidth() = movie.width()
    override fun getIntrinsicHeight() = movie.height()
    override fun draw(canvas: Canvas) {
        val duration = movie.duration().coerceAtLeast(100)
        movie.setTime(((if (playing) SystemClock.uptimeMillis() - started else elapsed) % duration).toInt())
        val checkpoint = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width().toFloat() / intrinsicWidth, bounds.height().toFloat() / intrinsicHeight)
        movie.draw(canvas, 0f, 0f, paint)
        canvas.restoreToCount(checkpoint)
    }
    override fun start() {
        if (playing) return
        playing = true
        started = SystemClock.uptimeMillis() - elapsed
        run()
    }
    override fun stop() {
        if (playing) elapsed = SystemClock.uptimeMillis() - started
        playing = false
        unscheduleSelf(this)
    }
    override fun isRunning() = playing
    override fun run() {
        if (!playing) return
        invalidateSelf()
        // Cap redraws to 30 fps on the Android 8 fallback.
        scheduleSelf(this, SystemClock.uptimeMillis() + 33)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

internal class WallpaperImageView(context: Context) : ImageView(context) {
    init { scaleType = ScaleType.CENTER_CROP; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun setImageDrawable(drawable: Drawable?) {
        if (this.drawable === drawable) return
        (this.drawable as? Animatable)?.stop()
        super.setImageDrawable(drawable)
        syncAnimation()
    }
    private fun syncAnimation() {
        (drawable as? Animatable)?.let {
            if (isAttachedToWindow && isShown && windowVisibility == VISIBLE && ValueAnimator.areAnimatorsEnabled()) it.start()
            else it.stop()
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() { (drawable as? Animatable)?.stop(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); syncAnimation() }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) syncAnimation() else (drawable as? Animatable)?.stop()
    }
}
