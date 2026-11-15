package dk.binumre.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.binumre.desktop.AppInfo
import java.awt.Desktop
import java.net.URI

/** Åbner programmets hjemmeside (hjælp og nye versioner) i browseren. */
fun openWebsite(state: AppState) {
    runCatching { Desktop.getDesktop().browse(URI(AppInfo.WEBSITE)) }
        .onFailure { state.error = state.strings.format("about.noBrowser", AppInfo.WEBSITE) }
}

/** "Om BiNumreGenerator": version, ophavsret, hjemmeside og licenser for de komponenter programmet bruger. */
@Composable
fun AboutDialog(state: AppState, onClose: () -> Unit) {
    val s = state.strings
    var license by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { ControlFlag(40.dp) },
        title = { Text(AppInfo.NAME) },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.format("about.version", AppInfo.version), style = MaterialTheme.typography.titleMedium)
                Text(s["start.tagline"], color = MaterialTheme.colorScheme.secondary)
                Text(AppInfo.COPYRIGHT + ". " + s["about.rights"])
                Text(s["about.website"], color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openWebsite(state) }) { Text(s["about.help"]) }
                    OutlinedButton(onClick = { openWebsite(state) }) { Text(s["about.update"]) }
                }
                Spacer(Modifier.height(4.dp))
                Text(s["about.licenses"], style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((label, file) in listOf(
                        s["about.overview"] to "THIRD-PARTY-NOTICES.txt", "Apache 2.0" to "Apache-2.0.txt",
                        "NOTICE" to "NOTICE.txt", "PDFBox" to "PDFBox-LICENSE.txt", "DejaVu" to "DejaVu-LICENSE.txt",
                    )) TextButton(onClick = { license = AppInfo.license(file) }) { Text(label) }
                }
                SelectionContainer {
                    Text(
                        license ?: AppInfo.license("THIRD-PARTY-NOTICES.txt"),
                        Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                            .verticalScroll(rememberScrollState()).padding(10.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text(s["ok"]) } },
    )
}
