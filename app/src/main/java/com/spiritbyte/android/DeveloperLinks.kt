package com.spiritbyte.android

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private data class DeveloperLink(val url: String, val icon: Int, val label: String)

private val developerLinks = listOf(
    DeveloperLink("https://www.paypal.com/paypalme/octacodec", R.drawable.folder_icon_heart, "Apoyar en PayPal"),
    DeveloperLink("https://github.com/OscarTired", R.drawable.ic_github, "GitHub de OscarTired"),
    DeveloperLink("https://octa-dev.com/", R.drawable.folder_icon_globe, "Portafolio de Octa_Dev")
)

@Composable
internal fun DeveloperLinks(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val palette = LocalDesktopPalette.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        developerLinks.forEach { link ->
            IconButton(
                onClick = {
                    try {
                        uriHandler.openUri(link.url)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No hay un navegador disponible para abrir este enlace.", Toast.LENGTH_LONG).show()
                    } catch (_: IllegalArgumentException) {
                        Toast.makeText(context, "No se pudo abrir el enlace en el navegador.", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = enabled,
                modifier = Modifier.size(48.dp).border(1.dp, palette.border, MaterialTheme.shapes.medium)
            ) {
                Icon(painterResource(link.icon), contentDescription = "${link.label}. Abre el navegador externo.",
                    modifier = Modifier.size(20.dp), tint = if (enabled) palette.dim else palette.dim.copy(alpha = .38f))
            }
        }
    }
}
