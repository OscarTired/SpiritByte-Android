package com.spiritbyte.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private data class FolderGlyph(val id: String, val label: String, val resource: Int)
private val folderGlyphs = listOf(
    FolderGlyph("folder", "Carpeta", R.drawable.folder_icon_folder), FolderGlyph("globe", "Web", R.drawable.folder_icon_globe),
    FolderGlyph("mail", "Correo", R.drawable.folder_icon_mail), FolderGlyph("shield", "Seguridad", R.drawable.folder_icon_shield),
    FolderGlyph("user", "Personal", R.drawable.folder_icon_user), FolderGlyph("wallet", "Billetera", R.drawable.folder_icon_wallet),
    FolderGlyph("briefcase", "Trabajo", R.drawable.folder_icon_briefcase), FolderGlyph("cloud", "Nube", R.drawable.folder_icon_cloud),
    FolderGlyph("gamepad", "Juegos", R.drawable.folder_icon_gamepad), FolderGlyph("key", "Llave", R.drawable.folder_icon_key),
    FolderGlyph("heart", "Corazón", R.drawable.folder_icon_heart), FolderGlyph("home", "Casa", R.drawable.folder_icon_home),
    FolderGlyph("music", "Música", R.drawable.folder_icon_music), FolderGlyph("camera", "Cámara", R.drawable.folder_icon_camera),
    FolderGlyph("code", "Código", R.drawable.folder_icon_code), FolderGlyph("book", "Libro", R.drawable.folder_icon_book),
    FolderGlyph("cart", "Compras", R.drawable.folder_icon_cart), FolderGlyph("server", "Servidor", R.drawable.folder_icon_server),
    FolderGlyph("phone", "Teléfono", R.drawable.folder_icon_phone), FolderGlyph("card", "Tarjeta", R.drawable.folder_icon_card),
    FolderGlyph("plane", "Viajes", R.drawable.folder_icon_plane), FolderGlyph("gift", "Regalo", R.drawable.folder_icon_gift)
)

fun folderTrail(folders: List<Folder>, id: String): String {
    val byId = folders.associateBy { it.id }
    val names = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    var next: String? = id
    while (next != null && seen.add(next)) {
        val folder = byId[next] ?: break
        names.add(folder.name); next = folder.parentId
    }
    return names.asReversed().joinToString(" / ")
}

fun folderDescendants(folders: List<Folder>, id: String): Set<String> {
    val children = folders.groupBy { it.parentId }
    val seen = mutableSetOf<String>()
    val pending = ArrayDeque<String>().apply { add(id) }
    while (pending.isNotEmpty()) {
        val next = pending.removeLast()
        if (seen.add(next)) children[next].orEmpty().forEach { pending.add(it.id) }
    }
    return seen
}

@Composable
fun FolderSymbol(folder: Folder, modifier: Modifier = Modifier.size(20.dp)) {
    val glyph = folderGlyphs.find { it.id == folder.icon } ?: folderGlyphs.first()
    val color = folder.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: MaterialTheme.colorScheme.primary
    Icon(painterResource(glyph.resource), contentDescription = null, tint = color, modifier = modifier)
}

@Composable
fun FolderPicker(folders: List<Folder>, selectedId: String?, onSelect: (String?) -> Unit,
    label: String = "Carpeta", allowAll: Boolean = false, excluded: Set<String> = emptySet(), enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    val selected = folders.find { it.id == selectedId }
    val title = if (selected != null) folderTrail(folders, selected.id)
        else if (allowAll && selectedId == null) "Todas las carpetas" else "Sin carpeta"
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                selected?.let { FolderSymbol(it); Spacer(Modifier.width(8.dp)) }
                Text(title, Modifier.weight(1f)); Text("⌄")
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 340.dp)) {
                if (allowAll) DropdownMenuItem(text = { Text("Todas las carpetas") }, onClick = { onSelect(null); expanded = false })
                DropdownMenuItem(text = { Text(if (label == "Carpeta padre") "Raíz (sin padre)" else "Sin carpeta") },
                    onClick = { onSelect(if (allowAll) "" else null); expanded = false })
                folders.filterNot { it.id in excluded }.sortedBy { folderTrail(folders, it.id).lowercase() }.forEach { folder ->
                    DropdownMenuItem(text = { Text(folderTrail(folders, folder.id)) }, leadingIcon = { FolderSymbol(folder) },
                        onClick = { onSelect(folder.id); expanded = false })
                }
            }
        }
    }
}

@Composable
fun FolderManager(model: VaultModel, close: () -> Unit) {
    var editing by remember { mutableStateOf<Folder?>(null) }
    BackHandler { if (editing != null) editing = null else close() }
    val current = editing
    if (current != null) {
        FolderEditor(current, model) { editing = null }
        return
    }
    Column {
        Text("Mis carpetas", style = MaterialTheme.typography.headlineMedium)
        Text("Organiza tus credenciales con los mismos iconos de escritorio.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = Folder() }, enabled = !model.busy) { Text("+ Carpeta") }
            TextButton(onClick = close) { Text("Volver") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (model.data.folders.isEmpty()) item { Text("Crea tu primera carpeta para organizar la bóveda.", Modifier.padding(vertical = 24.dp)) }
            items(model.data.folders.sortedBy { folderTrail(model.data.folders, it.id).lowercase() }, key = { it.id }) { folder ->
                Card(Modifier.fillMaxWidth().clickable(enabled = !model.busy) { editing = folder }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        FolderSymbol(folder, Modifier.size(26.dp)); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.name, style = MaterialTheme.typography.titleMedium)
                            if (folder.parentId != null) Text(folderTrail(model.data.folders, folder.id), style = MaterialTheme.typography.labelSmall)
                            Text("${model.data.entries.count { it.folderId == folder.id }} credenciales", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Editar", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderEditor(original: Folder, model: VaultModel, close: () -> Unit) {
    var folder by remember(original.id) { mutableStateOf(original) }
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Carpeta", style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FolderSymbol(folder, Modifier.size(34.dp)); Spacer(Modifier.width(12.dp))
            Text(folder.name.ifBlank { "Nueva carpeta" }, style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(folder.name, { folder = folder.copy(name = it) }, label = { Text("Nombre") },
            singleLine = true, enabled = !model.busy, modifier = Modifier.fillMaxWidth())
        FolderPicker(model.data.folders, folder.parentId, { folder = folder.copy(parentId = it) },
            label = "Carpeta padre", excluded = folderDescendants(model.data.folders, original.id), enabled = !model.busy)
        Text("Icono", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            folderGlyphs.forEach { glyph ->
                IconButton(onClick = { folder = folder.copy(icon = glyph.id) }, enabled = !model.busy,
                    modifier = Modifier.size(44.dp).border(1.dp, if (glyph.id == folder.icon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)) {
                    Icon(painterResource(glyph.resource), contentDescription = glyph.label, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text("Color", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<String?>(null, "#ffb000", "#39ff78", "#ff40a0", "#78c8ff", "#b080ff", "#ff6b6b", "#ffffff").forEach { color ->
                val swatch = color?.let { Color(android.graphics.Color.parseColor(it)) } ?: MaterialTheme.colorScheme.primary
                FilterChip(selected = folder.color == color, onClick = { folder = folder.copy(color = color) }, enabled = !model.busy,
                    label = { if (color == null) Text("Tema") else Box(Modifier.size(20.dp).background(swatch)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { model.saveFolder(folder.copy(name = folder.name.trim()), close) }, enabled = folder.name.isNotBlank() && !model.busy) { Text("Guardar") }
            TextButton(onClick = close, enabled = !model.busy) { Text("Cancelar") }
        }
        if (model.data.folders.any { it.id == original.id }) TextButton(onClick = { deleting = true }, enabled = !model.busy) { Text("Eliminar carpeta") }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("¿Eliminar carpeta?") },
        text = { Text("Sus credenciales se conservarán sin carpeta. Sus subcarpetas pasarán a la raíz. No se eliminarán contraseñas.") },
        confirmButton = { TextButton(onClick = { deleting = false; model.deleteFolder(original, close) }) { Text("Eliminar carpeta") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } })
}
