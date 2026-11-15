package dk.binumre.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dk.binumre.model.BibType

/** Første skærm: vælg løbstype (og antal etaper ved flerdagesløb), eller åbn et gemt projekt. */
@Composable
fun StartScreen(state: AppState, dialogs: FileDialogs) {
    val s = state.strings
    var stages by remember { mutableStateOf(3) }
    Box(Modifier.fillMaxSize()) {
        ContourBackground(Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) { LanguageMenu(state) }
        Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ControlFlag(64.dp)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("BiNumreGenerator", style = MaterialTheme.typography.headlineLarge)
                    Text(s["start.tagline"], style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(s["start.choose"], style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TypeCard(s["type.SINGLE"], s["start.single"], onClick = { state.start(BibType.SINGLE) }) {
                    MiniBib("101")
                }
                TypeCard(s["type.RELAY"], s["start.relay"], onClick = { state.start(BibType.RELAY) }) {
                    Box(Modifier.size(120.dp, 72.dp)) {
                        MiniBib("12", Color(0xFF22C55E), modifier = Modifier.offset(32.dp, 10.dp))
                        MiniBib("12", Color(0xFF3B82F6), modifier = Modifier.offset(16.dp, 5.dp))
                        MiniBib("12", Color(0xFFFFD400))
                    }
                }
                TypeCard(s["type.MULTIDAY"], s["start.multiday"], onClick = { state.start(BibType.MULTIDAY, stages) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MiniBib("2198", rows = minOf(stages, 6))
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedIconButton(onClick = { stages = (stages - 1).coerceAtLeast(2) }, Modifier.size(30.dp)) { Text("−") }
                            Text(s.format("top.stages", stages), Modifier.width(84.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                            OutlinedIconButton(onClick = { stages = (stages + 1).coerceAtMost(AppState.MAX_STAGES) }, Modifier.size(30.dp)) { Text("+") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick = { dialogs.open(s["dialog.openProject"], listOf("json")).firstOrNull()?.let(state::openProject) }) {
                Text(s["start.open"])
            }
        }
    }
}

@Composable
private fun TypeCard(title: String, text: String, onClick: () -> Unit, picture: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(250.dp).height(320.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(130.dp), contentAlignment = Alignment.Center) { picture() }
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onClick) { Text(title) }
        }
    }
}
