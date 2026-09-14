package com.spiritbyte.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spiritbyte.core.MobileVault
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FoldersTest {
    @Test fun foldersPersistIconsMoveEntriesAndDeleteWithoutLosingPasswords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.cacheDir.resolve("folders-test-${UUID.randomUUID()}").apply { mkdirs() }
        val vault = MobileVault(directory.absolutePath)
        val json = Json { ignoreUnknownKeys = true }
        try {
            vault.create("master folder password")
            vault.saveFolder("""{"id":"work","name":"Trabajo","icon":"briefcase","color":"#ffb000"}""")
            vault.saveFolder("""{"id":"mail","name":"Correo","parentId":"work","icon":"mail","color":"#78c8ff"}""")
            vault.saveEntry("""{"id":"e","title":"Correo laboral","folderId":"mail","password":"keep-me","createdAt":1,"updatedAt":1}""")
            val before = vault.entries()
            assertThrows(Exception::class.java) { vault.saveFolder("""{"id":"work","name":"Cycle","parentId":"mail"}""") }
            assertEquals(before, vault.entries())
            assertThrows(Exception::class.java) { vault.saveFolder("""{"id":"missing-parent","name":"No","parentId":"absent"}""") }
            vault.lock(); vault.unlock("master folder password")
            val read = json.decodeFromString<VaultData>(vault.entries())
            assertEquals("mail", read.folders.first { it.id == "mail" }.icon)
            assertEquals("#78c8ff", read.folders.first { it.id == "mail" }.color)
            assertEquals("Trabajo / Correo", folderTrail(read.folders, "mail"))
            vault.deleteFolder("work")
            val root = json.decodeFromString<VaultData>(vault.entries())
            assertNull(root.folders.single().parentId)
            assertEquals("mail", root.entries.single().folderId)
            vault.deleteFolder("mail")
            val unfiled = json.decodeFromString<VaultData>(vault.entries())
            assertNull(unfiled.entries.single().folderId)
            assertEquals("keep-me", unfiled.entries.single().password)
            assertTrue(unfiled.folders.isEmpty())
            vault.lock(); vault.unlock("master folder password")
            assertTrue(vault.entries().contains("keep-me"))
        } finally { vault.lock(); vault.close(); directory.deleteRecursively() }
    }
}
