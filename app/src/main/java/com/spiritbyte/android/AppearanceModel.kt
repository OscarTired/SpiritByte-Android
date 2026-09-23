package com.spiritbyte.android

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Appearance(
    val palette: String = "amber-crt",
    val font: String = "square",
    val background: String = "solid",
    val panelOpacity: Float = 0.90f,
    val scanlines: Boolean = true,
    val glow: Boolean = true,
    val showIntro: Boolean = true
)

/** Only visual preferences live here; never vault state or credentials. */
class AppearanceModel(app: Application) : AndroidViewModel(app) {
    private val preferences = app.getSharedPreferences("appearance", 0)
    private val wallpaperFile = app.filesDir.resolve("appearance/wallpaper.png")
    var settings by mutableStateOf(Appearance(
        palette = preferences.getString("palette", "amber-crt") ?: "amber-crt",
        font = preferences.getString("font", "square") ?: "square",
        background = preferences.getString("background", "solid") ?: "solid",
        panelOpacity = preferences.getFloat("panelOpacity", .90f).coerceIn(.60f, 1f),
        scanlines = preferences.getBoolean("scanlines", true),
        glow = preferences.getBoolean("glow", true),
        showIntro = preferences.getBoolean("showIntro", true)
    )); private set
    var wallpaper by mutableStateOf<Bitmap?>(null); private set
    var animatedWallpaper by mutableStateOf<Drawable?>(null); private set
    var hasWallpaper by mutableStateOf(wallpaperFile.exists()); private set
    private var loading: Job? = null
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    // Activity recreation and document pickers must not replay the intro.
    var introFinished by mutableStateOf(false)
    var replayIntro by mutableStateOf(false)
    var screenOpen by mutableStateOf(false)

    init {
        if (settings.background == "image") loadWallpaper()
    }

    private fun loadWallpaper() {
        loading?.cancel()
        loading = viewModelScope.launch {
            val asset = withContext(Dispatchers.IO) {
                runCatching {
                    // Recover an interrupted atomic write before decoding.
                    AtomicFile(wallpaperFile).openRead().close()
                    decodeAsset(wallpaperFile)
                }.getOrNull()
            }
            wallpaper = asset?.first
            animatedWallpaper = asset?.second
        }
    }

    fun update(value: Appearance) {
        val previousBackground = settings.background
        settings = value.copy(panelOpacity = value.panelOpacity.coerceIn(.60f, 1f))
        preferences.edit().putString("palette", settings.palette).putString("font", settings.font)
            .putString("background", settings.background).putFloat("panelOpacity", settings.panelOpacity)
            .putBoolean("scanlines", settings.scanlines).putBoolean("glow", settings.glow)
            .putBoolean("showIntro", settings.showIntro).apply()
        if (settings.background != "image") {
            loading?.cancel()
            wallpaper = null
            animatedWallpaper = null
        } else if (previousBackground != "image" && wallpaper == null && animatedWallpaper == null) {
            loadWallpaper()
        }
    }

    fun chooseWallpaper(uri: Uri?) {
        if (uri == null || busy) return
        loading?.cancel()
        busy = true; message = null
        viewModelScope.launch {
            try {
                val asset = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val temp = File.createTempFile("wallpaper-", ".input", app.cacheDir)
                    try {
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            temp.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var total = 0
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= 20 * 1024 * 1024) { "Elige una imagen de hasta 20 MB." }
                                    output.write(buffer, 0, count)
                                }
                            }
                        } ?: error("No se pudo abrir la imagen.")
                        val decoded = decodeAsset(temp)
                        wallpaperFile.parentFile?.mkdirs()
                        val atomic = AtomicFile(wallpaperFile)
                        val output = atomic.startWrite()
                        try {
                            if (decoded.second != null) temp.inputStream().use { it.copyTo(output) }
                            else check(requireNotNull(decoded.first).compress(Bitmap.CompressFormat.PNG, 100, output))
                            atomic.finishWrite(output)
                        } catch (e: Exception) { atomic.failWrite(output); throw e }
                        decoded
                    } finally { temp.delete() }
                }
                wallpaper = asset.first
                animatedWallpaper = asset.second
                hasWallpaper = true
                update(settings.copy(background = "image"))
                message = "Fondo guardado en este dispositivo."
            } catch (e: Exception) {
                message = e.message ?: "No se pudo cargar la imagen."
            } finally { busy = false }
        }
    }

    fun removeWallpaper() {
        if (busy) return
        loading?.cancel()
        busy = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) { AtomicFile(wallpaperFile).delete() }
            wallpaper = null
            animatedWallpaper = null
            hasWallpaper = false
            update(settings.copy(background = "solid"))
            busy = false; message = "Fondo eliminado."
        }
    }

    private fun decodeAsset(file: File): Pair<Bitmap?, Drawable?> {
        val header = ByteArray(6)
        file.inputStream().use { it.read(header) }
        val gif = header.contentEquals("GIF87a".toByteArray()) || header.contentEquals("GIF89a".toByteArray())
        return if (gif) null to decodeAnimatedWallpaper(file) else decodeWallpaper(file) to null
    }

    private fun decodeWallpaper(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "El archivo no es una imagen compatible." }
        val ratio = maxOf(bounds.outWidth, bounds.outHeight).toFloat() / 1280f
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val scale = maxOf(1f, maxOf(info.size.width, info.size.height).toFloat() / 1280f)
                decoder.setTargetSize((info.size.width / scale).toInt().coerceAtLeast(1), (info.size.height / scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        var sample = 1
        while (sample < ratio) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("No se pudo decodificar la imagen.")
        val exif = androidx.exifinterface.media.ExifInterface(file)
        val orientation = exif.rotationDegrees
        val flipped = exif.isFlipped
        if (orientation == 0 && !flipped) return decoded
        val matrix = android.graphics.Matrix().apply {
            if (flipped) postScale(-1f, 1f)
            postRotate(orientation.toFloat())
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }
}
