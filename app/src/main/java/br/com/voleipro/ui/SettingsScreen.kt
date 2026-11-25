package br.com.voleipro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.com.voleipro.domain.Appearance
import br.com.voleipro.domain.Preferences
import br.com.voleipro.domain.ThemePalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(preferences: Preferences, model: LeagueViewModel, showMessage: (String) -> Unit) {
    Page("Tema") {
        Panel {
            Text("Modo de exibição", style = MaterialTheme.typography.titleMedium)
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Appearance.entries.forEach { appearance ->
                    FilterChip(selected = preferences.appearance == appearance,
                        onClick = { model.preferences { it.copy(appearance = appearance) } },
                        label = { Text(appearance.label) })
                }
            }
            HorizontalDivider()
            Text("Paleta de cores", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemePalette.entries.forEach { palette ->
                    PaletteOption(palette, selected = preferences.palette == palette) {
                        model.preferences { it.copy(palette = palette) }
                    }
                }
            }
            if (preferences.appearance == Appearance.CONTRAST) {
                Text("O alto contraste usa preto e branco.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AboutPanel(showMessage)
    }
}

@Composable
private fun PaletteOption(palette: ThemePalette, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(palette.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            palette.swatches().forEach { ColorSwatch(it) }
        }
        RadioButton(selected, onClick = null)
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Surface(Modifier.size(18.dp), shape = CircleShape, color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {}
}

@Composable
private fun AboutPanel(showMessage: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val email = "laurobrizidio@hotmail.com"
    val linkedInProfile = "linkedin.com/in/laurobrizidio"

    fun openContact(uri: String, errorMessage: String) {
        try {
            uriHandler.openUri(uri)
        } catch (_: IllegalArgumentException) {
            showMessage(errorMessage)
        }
    }

    Panel {
        Text("Sobre o Vôlei Pro", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Lauro Brizidio", style = MaterialTheme.typography.titleLarge)
            Text("Desenvolvedor Android", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column {
            ContactRow(Icons.Default.Phone, "Telefone", "(92) 99379-5337") {
                openContact("tel:+5592993795337", "Não foi possível abrir o discador.")
            }
            ContactRow(Icons.Default.Email, "E-mail", email) {
                openContact("mailto:$email", "Não foi possível abrir um aplicativo de e-mail.")
            }
            ContactRow(Icons.Default.AccountBox, "LinkedIn", linkedInProfile) {
                openContact("https://www.$linkedInProfile/", "Não foi possível abrir o LinkedIn.")
            }
        }
    }
}

@Composable
private fun ContactRow(icon: ImageVector, label: String, detail: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick),
        headlineContent = { Text(label) },
        supportingContent = { Text(detail) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent,
            leadingIconColor = MaterialTheme.colorScheme.primary),
    )
}
