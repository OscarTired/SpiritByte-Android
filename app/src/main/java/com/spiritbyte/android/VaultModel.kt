package com.spiritbyte.android

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spiritbyte.core.MobileVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class Entry(
    val id: String = UUID.randomUUID().toString(), val title: String = "",
    val username: String = "", val password: String = "", val url: String = "",
    val notes: String = "", val folderId: String? = null, val iconId: String? = null,
    val favorite: Boolean = false, val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
@Serializable data class Folder(
    val id: String = UUID.randomUUID().toString(), val name: String = "",
    val parentId: String? = null, val icon: String? = "folder", val color: String? = null
)
@Serializable data class VaultData(val entries: List<Entry> = emptyList(), val folders: List<Folder> = emptyList())

class VaultModel(app: Application) : AndroidViewModel(app) {
    private val native = MobileVault(app.filesDir.resolve("vault").absolutePath)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val operations = Mutex()
    private val epoch = AtomicInteger()
    var screenGeneration by mutableIntStateOf(0); private set
    var exists by mutableStateOf(native.exists()); private set
    var unlocked by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var recovery by mutableStateOf<String?>(null); private set
    var data by mutableStateOf(VaultData()); private set
    var importUri by mutableStateOf<Uri?>(null)
    // Only authenticated ciphertext survives the external document picker.
    private var pendingExport: String? = null

    private fun <T> run(work: () -> T, allowWhileBusy: Boolean = false, done: (T) -> Unit = {}) {
        if (busy && !allowWhileBusy) return
        val ticket = epoch.get()
        busy = true; message = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { operations.withLock {
                    check(epoch.get() == ticket) { "Operación cancelada al bloquear." }
                    work()
                } }
                if (epoch.get() == ticket) done(result)
            } catch (e: Exception) {
                if (epoch.get() == ticket) message = e.message ?: "No se pudo completar la operación."
            } finally {
                exists = native.exists()
                if (epoch.get() == ticket) busy = false
            }
        }
    }

    fun create(password: String) = run({ native.create(password) }) {
        recovery = it; unlocked = true; data = VaultData()
    }
    fun unlock(password: String) = run({ native.unlock(password); read() }) { data = it; unlocked = true }
    fun recover(phrase: String, password: String) = run({ native.recover(phrase, password); read() }) {
        data = it; unlocked = true
    }
    fun confirmRecovery() { recovery = null }
    private fun read() = json.decodeFromString<VaultData>(native.entries())
    fun save(entry: Entry, done: () -> Unit) = run({
        native.saveEntry(json.encodeToString(entry.copy(updatedAt = System.currentTimeMillis()))); read()
    }) { data = it; done() }
    fun delete(entry: Entry, done: () -> Unit) = run({ native.deleteEntry(entry.id); read() }) { data = it; done() }
    fun saveFolder(folder: Folder, done: () -> Unit) = run({ native.saveFolder(json.encodeToString(folder)); read() }) { data = it; done() }
    fun deleteFolder(folder: Folder, done: () -> Unit) = run({ native.deleteFolder(folder.id); read() }) { data = it; done() }
    fun generate(done: (String) -> Unit) = run({ native.generatePassword(24u) }, done = done)
    fun prepareExport(password: String, launch: () -> Unit) = run({ native.exportBackup(password) }) {
        pendingExport = it; launch()
    }
    fun writeExport(uri: Uri?) {
        val contents = pendingExport
        pendingExport = null
        if (uri == null || contents == null) return
        run({
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(contents.toByteArray(Charsets.UTF_8))
            } ?: error("No se pudo escribir el archivo.")
        }, allowWhileBusy = true) { message = "Backup cifrado exportado." }
    }
    fun importBackup(password: String) {
        val uri = importUri ?: return
        run({
            val resolver = getApplication<Application>().contentResolver
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 32 * 1024 * 1024) { "El backup supera 32 MiB." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("No se pudo leer el archivo.")
            native.importBackup(bytes.toString(Charsets.UTF_8), password)
            read()
        }) { data = it; importUri = null; message = "Backup importado. Se añadieron sus registros." }
    }

    fun lock() {
        val ticket = epoch.incrementAndGet()
        screenGeneration++
        unlocked = false; data = VaultData(); recovery = null; message = null; busy = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) { operations.withLock { native.lock() } }
            if (ticket == epoch.get()) busy = false
        }
    }
}
