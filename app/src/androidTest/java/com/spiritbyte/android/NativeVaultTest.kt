package com.spiritbyte.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spiritbyte.core.MobileVault
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NativeVaultTest {
    @Test fun nativeLibraryCreatesPersistsRecoversAndImports() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.cacheDir.resolve("native-test-${UUID.randomUUID()}").apply { mkdirs() }
        val vault = MobileVault(directory.absolutePath)
        try {
            assertFalse(vault.exists())
            val started = System.nanoTime()
            val phrase = vault.create("test master password 123")
            assertEquals(12, phrase.split(" ").size)
            vault.saveEntry("""{"id":"test","title":"Correo","password":"test secret","createdAt":1,"updatedAt":1}""")
            val backup = vault.exportBackup("test backup password 123")
            assertFalse(backup.contains("test secret"))
            vault.lock()
            assertThrows(Exception::class.java) { vault.entries() }
            assertThrows(Exception::class.java) { vault.unlock("incorrect") }
            vault.unlock("test master password 123")
            assertTrue(vault.entries().contains("Correo"))
            val before = vault.entries()
            assertThrows(Exception::class.java) { vault.importBackup(backup, "incorrect") }
            assertEquals(before, vault.entries())
            vault.importBackup(backup, "test backup password 123")
            assertEquals(2, org.json.JSONObject(vault.entries()).getJSONArray("entries").length())
            vault.lock()
            vault.recover(phrase, "replacement password 123")
            vault.lock()
            vault.unlock("replacement password 123")
            assertTrue(vault.entries().contains("Correo"))
            android.util.Log.i("SpiritByteTest", "Native roundtrip ms: ${(System.nanoTime() - started) / 1_000_000}")
        } finally {
            vault.lock(); vault.close()
            directory.deleteRecursively()
        }
    }
}
