package com.spiritbyte.android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppearanceAssetsTest {
    @Test fun desktopFontsAndFoxLoadOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(R.font.geist_pixel_square, R.font.geist_pixel_grid, R.font.geist_pixel_circle,
            R.font.geist_pixel_triangle, R.font.geist_pixel_line).forEach {
            assertNotNull(ResourcesCompat.getFont(context, it))
        }
        val palette = desktopPalettes.first()
        val fox = foxArtwork(context, palette.primary, palette.accent)
        val pixels = IntArray(420 * 420)
        fox.readPixels(pixels)
        assertTrue("The original fox must produce visible pixels", pixels.count { (it ushr 24) > 0 } > 2000)
        assertTrue("The background around the fox remains transparent", pixels.count { (it ushr 24) == 0 } > 2000)
    }

    private class IsolatedApp(context: Context, private val directory: File) : Application() {
        init { attachBaseContext(context) }
        override fun getFilesDir(): File = directory
        override fun getCacheDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("${directory.name}-$name", mode)
    }

    @Test fun wallpaperIsBoundedPersistentAndInvalidInputPreservesIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = context.cacheDir.resolve("appearance-test-${UUID.randomUUID()}").apply { mkdirs() }
        val app = IsolatedApp(context, directory)
        val source = directory.resolve("source.png")
        val bitmap = Bitmap.createBitmap(4096, 512, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val store = ViewModelStore()
        lateinit var model: AppearanceModel
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun await(predicate: () -> Boolean) {
            repeat(400) {
                var ready = false
                main { ready = predicate() }
                if (ready) return
                Thread.sleep(25)
            }
            fail("Wallpaper operation timed out")
        }
        try {
            main { model = AppearanceModel(app); store.put("initial", model); model.chooseWallpaper(Uri.fromFile(source)) }
            await { !model.busy }
            main {
                assertEquals("image", model.settings.background)
                assertNotNull(model.wallpaper)
                assertTrue(model.wallpaper!!.width <= 1280)
                model.update(model.settings.copy(font = "grid", palette = "synthwave"))
            }
            val invalid = directory.resolve("invalid.png").apply { writeText("not an image") }
            main { model.chooseWallpaper(Uri.fromFile(invalid)) }
            await { !model.busy }
            main { assertNotNull(model.message); assertNotNull(model.wallpaper); assertEquals("image", model.settings.background) }
            main { model = AppearanceModel(app); store.put("reopened", model) }
            await { model.wallpaper != null }
            main { assertEquals("grid", model.settings.font); assertEquals("synthwave", model.settings.palette); model.removeWallpaper() }
            await { !model.busy }
            main { assertEquals("solid", model.settings.background); assertNull(model.wallpaper) }
        } finally {
            main { store.clear() }
            context.deleteSharedPreferences("${directory.name}-appearance")
            directory.deleteRecursively()
        }
    }

    @Test fun gifKeepsAnimationAcrossReloadAndReleasesInactiveBackground() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = context.cacheDir.resolve("gif-test-${UUID.randomUUID()}").apply { mkdirs() }
        val app = IsolatedApp(context, directory)
        // Two 1x1 frames (red, green), 100 ms each, looping forever.
        val encoded = ("47494638396101000100800000ff000000ff00" +
            "21ff0b4e45545343415045322e300301000000" +
            "21f904000a0000002c0000000001000100000202440100" +
            "21f904000a0000002c00000000010001000002024c01003b")
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val source = directory.resolve("source.gif").apply { writeBytes(encoded) }
        val store = ViewModelStore()
        lateinit var model: AppearanceModel
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun await(predicate: () -> Boolean) {
            repeat(400) {
                var ready = false
                main { ready = predicate() }
                if (ready) return
                Thread.sleep(25)
            }
            fail("GIF operation timed out")
        }
        try {
            main { model = AppearanceModel(app); store.put("initial", model); model.chooseWallpaper(Uri.fromFile(source)) }
            await { !model.busy }
            main {
                assertTrue(model.hasWallpaper)
                assertTrue(model.animatedWallpaper is Animatable)
                assertNull(model.wallpaper)
                val animation = model.animatedWallpaper as Animatable
                val view = WallpaperImageView(context)
                view.setImageDrawable(model.animatedWallpaper)
                animation.start()
                assertTrue(animation.isRunning)
                view.onVisibilityAggregated(false)
                assertFalse(animation.isRunning)
                animation.start()
                view.setImageDrawable(null)
                assertFalse(animation.isRunning)
            }
            assertArrayEquals(encoded, directory.resolve("appearance/wallpaper.png").readBytes())
            main { model = AppearanceModel(app); store.put("reopened", model) }
            await { model.animatedWallpaper != null }
            main {
                assertTrue(model.animatedWallpaper is Animatable)
                model.update(model.settings.copy(background = "solid"))
                assertNull(model.wallpaper)
                assertNull(model.animatedWallpaper)
                assertTrue(model.hasWallpaper)
                model.update(model.settings.copy(background = "image"))
            }
            await { model.animatedWallpaper != null }
            val invalid = directory.resolve("broken.gif").apply { writeText("GIF89a broken") }
            main { model.chooseWallpaper(Uri.fromFile(invalid)) }
            await { !model.busy }
            main { assertNotNull(model.message); assertTrue(model.animatedWallpaper is Animatable) }
            assertArrayEquals(encoded, directory.resolve("appearance/wallpaper.png").readBytes())
            main { model.removeWallpaper() }
            await { !model.busy }
            main { assertFalse(model.hasWallpaper); assertNull(model.animatedWallpaper) }
        } finally {
            main { store.clear() }
            context.deleteSharedPreferences("${directory.name}-appearance")
            directory.deleteRecursively()
        }
    }
}
