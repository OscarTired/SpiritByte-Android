package com.spiritbyte.android

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SessionLifecycleTest {
    private class IsolatedApp(private val directory: File) : Application() {
        override fun getFilesDir(): File = directory
    }

    @Test fun lockDiscardsInFlightUnlockAndVisibleSecrets() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = instrumentation.targetContext.cacheDir.resolve("session-test-${UUID.randomUUID()}").apply { mkdirs() }
        lateinit var model: VaultModel
        val store = ViewModelStore()
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun awaitIdle() {
            val deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline) {
                var idle = false
                main { idle = !model.busy }
                if (idle) return
                Thread.sleep(25)
            }
            fail("Native operation did not complete within 30 seconds")
        }
        try {
            main { model = VaultModel(IsolatedApp(directory)); store.put("test", model); model.create("master password for lifecycle") }
            awaitIdle()
            main { assertTrue(model.unlocked); assertNotNull(model.recovery); model.lock() }
            awaitIdle()
            main { model.unlock("master password for lifecycle") }
            Thread.sleep(30)
            main { model.lock(); assertFalse(model.unlocked); assertTrue(model.data.entries.isEmpty()); assertNull(model.recovery) }
            awaitIdle()
            main { assertFalse(model.unlocked); assertNull(model.recovery); model.unlock("master password for lifecycle") }
            awaitIdle()
            main { assertTrue(model.unlocked); model.lock() }
            awaitIdle()
        } finally {
            main { store.clear() }
            directory.deleteRecursively()
        }
    }
}
