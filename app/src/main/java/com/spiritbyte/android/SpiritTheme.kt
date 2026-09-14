package com.spiritbyte.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

data class DesktopPalette(val id: String, val name: String, val bg: Color, val surface: Color,
    val border: Color, val text: Color, val dim: Color, val primary: Color, val accent: Color)

val desktopPalettes = listOf(
    DesktopPalette("amber-crt", "Amber CRT", Color(0xFF0D0C0A), Color(0xFF1A1712), Color(0xFF524226), Color(0xFFF5D696), Color(0xFFA88C5C), Color(0xFFFFB000), Color(0xFFFFD566)),
    DesktopPalette("green-phosphor", "Green Phosphor", Color(0xFF080C09), Color(0xFF0E1610), Color(0xFF264A2E), Color(0xFFB4FFBE), Color(0xFF68A874), Color(0xFF39FF78), Color(0xFF96FFB4)),
    DesktopPalette("synthwave", "Synthwave 84", Color(0xFF100A1C), Color(0xFF1A102C), Color(0xFF5C3282), Color(0xFFF0DCFF), Color(0xFFA882D2), Color(0xFFFF40A0), Color(0xFF3CDCF0)),
    DesktopPalette("ibm-noir", "IBM Noir", Color(0xFF0A0C10), Color(0xFF12161C), Color(0xFF3A424E), Color(0xFFD2DCE6), Color(0xFF8290A0), Color(0xFF78C8FF), Color(0xFF78FFDC)),
    DesktopPalette("commodore", "Commodore 64", Color(0xFF141040), Color(0xFF201C5C), Color(0xFF6C64C8), Color(0xFFBCB8FF), Color(0xFF8682D2), Color(0xFF96DCFF), Color(0xFFFFFFA0))
)
val LocalAppearance = staticCompositionLocalOf { Appearance() }
val LocalDesktopPalette = staticCompositionLocalOf { desktopPalettes.first() }

@Composable
fun SpiritTheme(settings: Appearance, content: @Composable () -> Unit) {
    val palette = desktopPalettes.find { it.id == settings.palette } ?: desktopPalettes.first()
    val family = when (settings.font) {
        "grid" -> FontFamily(Font(R.font.geist_pixel_grid))
        "circle" -> FontFamily(Font(R.font.geist_pixel_circle))
        "triangle" -> FontFamily(Font(R.font.geist_pixel_triangle))
        "line" -> FontFamily(Font(R.font.geist_pixel_line))
        "system" -> FontFamily.Monospace
        else -> FontFamily(Font(R.font.geist_pixel_square))
    }
    val type = Typography()
    val typography = Typography(
        displayLarge = type.displayLarge.copy(fontFamily = family), displayMedium = type.displayMedium.copy(fontFamily = family),
        displaySmall = type.displaySmall.copy(fontFamily = family), headlineLarge = type.headlineLarge.copy(fontFamily = family),
        headlineMedium = type.headlineMedium.copy(fontFamily = family), headlineSmall = type.headlineSmall.copy(fontFamily = family),
        titleLarge = type.titleLarge.copy(fontFamily = family), titleMedium = type.titleMedium.copy(fontFamily = family),
        titleSmall = type.titleSmall.copy(fontFamily = family), bodyLarge = type.bodyLarge.copy(fontFamily = family),
        bodyMedium = type.bodyMedium.copy(fontFamily = family), bodySmall = type.bodySmall.copy(fontFamily = family),
        labelLarge = type.labelLarge.copy(fontFamily = family), labelMedium = type.labelMedium.copy(fontFamily = family),
        labelSmall = type.labelSmall.copy(fontFamily = family)
    )
    CompositionLocalProvider(LocalAppearance provides settings, LocalDesktopPalette provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(primary = palette.primary, onPrimary = palette.bg,
                primaryContainer = palette.border, onPrimaryContainer = palette.text,
                secondary = palette.accent, onSecondary = palette.bg,
                secondaryContainer = palette.border, onSecondaryContainer = palette.text,
                background = palette.bg, onBackground = palette.text,
                surface = palette.surface, onSurface = palette.text,
                surfaceVariant = palette.surface, onSurfaceVariant = palette.text,
                outline = palette.border, outlineVariant = palette.border),
            typography = typography,
            shapes = Shapes(extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(3.dp),
                medium = RoundedCornerShape(4.dp), large = RoundedCornerShape(5.dp), extraLarge = RoundedCornerShape(6.dp)),
            content = { CompositionLocalProvider(LocalContentColor provides palette.text, content = content) }
        )
    }
}

@Composable
fun DesktopBackground(model: AppearanceModel, content: @Composable BoxScope.() -> Unit) {
    val palette = LocalDesktopPalette.current
    val settings = model.settings
    Box(Modifier.fillMaxSize().background(palette.bg)) {
        if (settings.background == "gradient") Box(Modifier.matchParentSize().background(
            Brush.linearGradient(listOf(palette.primary.copy(alpha = .24f), palette.bg, palette.accent.copy(alpha = .18f)))))
        if (settings.background == "image") model.wallpaper?.let {
            Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(palette.bg.copy(alpha = .30f)))
        }
        content()
        if (settings.scanlines) Canvas(Modifier.matchParentSize()) {
            var y = 0f
            while (y < size.height) {
                drawLine(Color.Black.copy(alpha = .12f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += 4.dp.toPx()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(model: AppearanceModel, chooseImage: () -> Unit, close: () -> Unit) {
    val settings = model.settings
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Apariencia", style = MaterialTheme.typography.headlineMedium)
        Text("El mismo espíritu. Otra pantalla.", color = MaterialTheme.colorScheme.secondary)
        Text("01 / PALETA", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            desktopPalettes.forEach { palette ->
                FilterChip(settings.palette == palette.id, onClick = { model.update(settings.copy(palette = palette.id)) },
                    label = { Text(palette.name) }, leadingIcon = { Box(Modifier.size(10.dp).background(palette.primary)) })
            }
        }
        Text("02 / TIPOGRAFÍA", style = MaterialTheme.typography.labelLarge)
        Text("Geist Pixel", style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("square", "grid", "circle", "triangle", "line", "system").forEach { font ->
                FilterChip(settings.font == font, onClick = { model.update(settings.copy(font = font)) },
                    label = { Text(if (font == "system") "Sistema" else font.replaceFirstChar { it.uppercase() }) })
            }
        }
        Text("Aa Bb 0123456789 · @#$% · ñ á é", color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text("03 / FONDO", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("solid" to "Sólido", "gradient" to "Degradado", "image" to "Imagen").forEach { (id, label) ->
                FilterChip(settings.background == id, onClick = {
                    if (id == "image" && model.wallpaper == null) chooseImage()
                    else model.update(settings.copy(background = id))
                }, label = { Text(label) }, enabled = !model.busy)
            }
        }
        OutlinedButton(onClick = chooseImage, enabled = !model.busy) { Text("Elegir imagen del dispositivo") }
        if (model.wallpaper != null) TextButton(onClick = model::removeWallpaper, enabled = !model.busy) { Text("Eliminar imagen guardada") }
        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        model.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        Text("Opacidad de los paneles · ${(settings.panelOpacity * 100).toInt()}%")
        Slider(value = settings.panelOpacity, onValueChange = { model.update(settings.copy(panelOpacity = it)) }, valueRange = .60f..1f)
        HorizontalDivider()
        Text("04 / EFECTOS", style = MaterialTheme.typography.labelLarge)
        SettingToggle("Líneas CRT", settings.scanlines) { model.update(settings.copy(scanlines = it)) }
        SettingToggle("Brillo del zorro", settings.glow) { model.update(settings.copy(glow = it)) }
        SettingToggle("Intro del zorro al iniciar", settings.showIntro) { model.update(settings.copy(showIntro = it)) }
        OutlinedButton(onClick = { model.replayIntro = true }) { Text("Ver intro ahora") }
        Text("Geist Pixel · The Geist Project Authors · SIL OFL 1.1", style = MaterialTheme.typography.labelSmall)
        Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingToggle(label: String, value: Boolean, update: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, update)
    }
}
