package com.spiritbyte.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val model by viewModels<VaultModel>()
    private val appearance by viewModels<AppearanceModel>()
    private val handler = Handler(Looper.getMainLooper())
    private val idleLock = Runnable { model.lock() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Avoid the OS offering to save the master password or entry editor.
        window.decorView.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setContent {
            SpiritTheme(appearance.settings) {
                val palette = LocalDesktopPalette.current
                SideEffect { window.statusBarColor = palette.bg.toArgb(); window.navigationBarColor = palette.bg.toArgb() }
                if (appearance.replayIntro || (appearance.settings.showIntro && !appearance.introFinished)) {
                    FoxIntro { appearance.introFinished = true; appearance.replayIntro = false }
                } else SpiritByte(model, appearance)
            }
        }
    }
    override fun onResume() { super.onResume(); resetIdle() }
    override fun onUserInteraction() { super.onUserInteraction(); resetIdle() }
    private fun resetIdle() { handler.removeCallbacks(idleLock); handler.postDelayed(idleLock, 120_000) }
    override fun onStop() {
        model.lock()
        handler.removeCallbacks(idleLock)
        super.onStop()
    }
}

@Composable
private fun SpiritByte(model: VaultModel, appearance: AppearanceModel) {
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        model.writeExport(it)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { model.importUri = it }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), appearance::chooseWallpaper)
    val palette = LocalDesktopPalette.current
    BackHandler(enabled = appearance.screenOpen) { appearance.screenOpen = false }
    DesktopBackground(appearance) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp)
            .background(palette.surface.copy(alpha = appearance.settings.panelOpacity), MaterialTheme.shapes.medium)
            .border(1.dp, palette.border, MaterialTheme.shapes.medium).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Image(painterResource(R.drawable.desktop_logo), contentDescription = "Logo de SpiritByte",
                    modifier = Modifier.padding(end = 10.dp).size(38.dp))
                Column(Modifier.weight(1f)) {
                    Text("SPIRITBYTE", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text("LOCAL / PRIVATE / YOURS", style = MaterialTheme.typography.labelSmall, color = palette.dim)
                }
                TextButton(onClick = { appearance.screenOpen = !appearance.screenOpen }) { Text("Tema") }
            }
            if (model.unlocked && !appearance.screenOpen) TextButton(onClick = model::lock) { Text("[ Bloquear bóveda ]") }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            model.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 8.dp)) }
            key(model.screenGeneration) { when {
                appearance.screenOpen -> AppearanceScreen(appearance, chooseImage = { wallpaperPicker.launch(arrayOf("image/*")) }, close = { appearance.screenOpen = false })
                !model.unlocked -> UnlockScreen(model)
                model.recovery != null -> RecoveryScreen(model)
                else -> VaultScreen(model,
                    export = { password -> model.prepareExport(password) { exportPicker.launch("SpiritByte-${System.currentTimeMillis()}.spiritbyte") } },
                    selectImport = { importPicker.launch(arrayOf("*/*")) })
            } }
        }
    }
}

@Composable
private fun SecretField(value: String, changed: (String) -> Unit, label: String, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, changed, label = { Text(label) }, singleLine = true,
        enabled = enabled, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Ver") } })
}

@Composable
private fun UnlockScreen(model: VaultModel) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var recovering by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (!model.exists) "Un lugar para tus secretos." else "Bienvenido de nuevo.", style = MaterialTheme.typography.headlineMedium)
        Text(if (!model.exists) "Crea una contraseña maestra de al menos 12 caracteres. La necesitarás para abrir tu bóveda." else "Desbloquea tu bóveda para acceder a tus credenciales.")
        if (model.importUri != null) Text("Backup seleccionado. Desbloquea para importarlo.")
        if (recovering) SecretField(phrase, { phrase = it }, "Tus 12 palabras de recuperación", !model.busy)
        SecretField(password, { password = it }, if (recovering) "Nueva contraseña maestra" else "Contraseña maestra", !model.busy)
        if (!model.exists || recovering) SecretField(confirm, { confirm = it }, "Repite la contraseña", !model.busy)
        val valid = if (!model.exists || recovering) password.codePointCount(0, password.length) >= 12 && password == confirm && (!recovering || phrase.isNotBlank()) else password.isNotEmpty()
        Button(onClick = {
            when { !model.exists -> model.create(password); recovering -> model.recover(phrase, password); else -> model.unlock(password) }
            password = ""; confirm = ""; phrase = ""
        }, enabled = valid && !model.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (!model.exists) "Crear bóveda" else if (recovering) "Recuperar acceso" else "Desbloquear")
        }
        if (model.exists) TextButton(onClick = { recovering = !recovering; password = ""; confirm = ""; phrase = "" }, enabled = !model.busy) {
            Text(if (recovering) "Usar mi contraseña" else "Olvidé mi contraseña")
        }
        Text("Sin cuentas. Sin servidores. Sin conexión.", color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun RecoveryScreen(model: VaultModel) {
    var acknowledged by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Guarda tu recuperación", style = MaterialTheme.typography.headlineMedium)
        Text("Estas 12 palabras permiten recuperar toda tu bóveda. Anótalas en un lugar privado antes de continuar. Se muestran una sola vez; no cambies de aplicación todavía.")
        Card { Text(model.recovery.orEmpty(), Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge) }
        Row { Checkbox(acknowledged, { acknowledged = it }); Text("He guardado las 12 palabras.", Modifier.padding(top = 12.dp)) }
        Button(onClick = model::confirmRecovery, enabled = acknowledged) { Text("Entrar a mi bóveda") }
    }
}

@Composable
private fun VaultScreen(model: VaultModel, export: (String) -> Unit, selectImport: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var backups by remember { mutableStateOf(false) }
    var folderManager by remember { mutableStateOf(false) }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(model.data.folders, folderFilter) {
        if (!folderFilter.isNullOrEmpty() && model.data.folders.none { it.id == folderFilter }) folderFilter = null
    }
    if (folderManager) { FolderManager(model) { folderManager = false }; return }
    val current = editing
    if (current != null) {
        EntryEditor(current, model, close = { editing = null })
        return
    }
    if (backups || model.importUri != null) {
        BackupScreen(model, export, selectImport) { backups = false; model.importUri = null }
        return
    }
    Text("Mi bóveda", style = MaterialTheme.typography.headlineMedium)
    Text("${model.data.entries.size} credenciales", color = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(query, { query = it }, label = { Text("Buscar título, usuario o sitio") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    FolderPicker(model.data.folders, folderFilter, { folderFilter = it }, allowAll = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(favorites, onClick = { favorites = !favorites }, label = { Text("Favoritos") })
        TextButton(onClick = { backups = true }) { Text("Backups") }
        TextButton(onClick = { editing = Entry(folderId = folderFilter?.takeIf { it.isNotEmpty() }) }) { Text("+ Nueva") }
    }
    TextButton(onClick = { folderManager = true }) { Text("Administrar carpetas e iconos") }
    val entries = model.data.entries.filter {
        (!favorites || it.favorite) && (folderFilter == null || (folderFilter == "" && it.folderId == null) || it.folderId == folderFilter) &&
            listOf(it.title, it.username, it.url).any { field -> field.contains(query, ignoreCase = true) }
    }.sortedBy { it.title.lowercase() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (entries.isEmpty()) item {
            Text(if (model.data.entries.isEmpty()) "Tu bóveda está lista. Añade tu primera credencial o importa un backup de escritorio." else "No hay coincidencias.", Modifier.padding(vertical = 30.dp))
        }
        items(entries, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth().clickable { editing = entry }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text((if (entry.favorite) "★ " else "") + entry.title, style = MaterialTheme.typography.titleMedium)
                    Text(entry.username.ifBlank { entry.url.ifBlank { "Sin usuario" } }, color = MaterialTheme.colorScheme.secondary)
                    model.data.folders.find { it.id == entry.folderId }?.let { folder ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            FolderSymbol(folder, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(folderTrail(model.data.folders, folder.id), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryEditor(original: Entry, model: VaultModel, close: () -> Unit) {
    var entry by remember(original.id) { mutableStateOf(original) }
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Credencial", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(entry.title, { entry = entry.copy(title = it) }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(entry.username, { entry = entry.copy(username = it) }, label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth())
        FolderPicker(model.data.folders, entry.folderId, { entry = entry.copy(folderId = it) }, enabled = !model.busy)
        SecretField(entry.password, { entry = entry.copy(password = it) }, "Contraseña")
        TextButton(onClick = { model.generate { entry = entry.copy(password = it) } }, enabled = !model.busy) { Text("Generar contraseña de 24 caracteres") }
        OutlinedTextField(entry.url, { entry = entry.copy(url = it) }, label = { Text("Sitio web") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(entry.notes, { entry = entry.copy(notes = it) }, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Row { Checkbox(entry.favorite, { entry = entry.copy(favorite = it) }); Text("Favorito", Modifier.padding(top = 12.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { model.save(entry, close) }, enabled = entry.title.isNotBlank() && !model.busy) { Text("Guardar") }
            TextButton(onClick = close, enabled = !model.busy) { Text("Cancelar") }
            if (model.data.entries.any { it.id == original.id }) TextButton(onClick = { deleting = true }, enabled = !model.busy) { Text("Eliminar") }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("¿Eliminar credencial?") },
        text = { Text("Se eliminará de esta bóveda al guardar el cambio.") },
        confirmButton = { TextButton(onClick = { deleting = false; model.delete(original, close) }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } })
}

@Composable
private fun BackupScreen(model: VaultModel, export: (String) -> Unit, selectImport: () -> Unit, close: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Backups cifrados", style = MaterialTheme.typography.headlineMedium)
        Text("Intercambia archivos .spiritbyte con tu escritorio. La contraseña del backup es independiente de la contraseña maestra y de la frase de recuperación.")
        if (model.importUri != null) {
            Text("Archivo seleccionado. La importación añade registros; repetirla crea duplicados.")
            SecretField(password, { password = it }, "Contraseña del backup")
            Button(onClick = { model.importBackup(password); password = "" }, enabled = password.isNotEmpty() && !model.busy) { Text("Importar y añadir") }
        } else {
            Text("Exportar · mínimo 12 caracteres")
            SecretField(password, { password = it }, "Contraseña del backup")
            SecretField(confirm, { confirm = it }, "Repite la contraseña")
            Button(onClick = { export(password); password = ""; confirm = "" }, enabled = password.codePointCount(0, password.length) >= 12 && password == confirm && !model.busy) { Text("Exportar archivo") }
            HorizontalDivider()
            Text("Al abrir el selector de archivos, la bóveda se bloqueará. Desbloquéala de nuevo para importar.")
            OutlinedButton(onClick = selectImport, enabled = !model.busy) { Text("Seleccionar backup para importar") }
        }
        TextButton(onClick = close, enabled = !model.busy) { Text("Volver") }
    }
}
