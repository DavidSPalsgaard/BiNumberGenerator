package dk.binumre.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dk.binumre.layout.BibTexts
import dk.binumre.model.BibProject
import dk.binumre.model.BibType
import dk.binumre.model.Sheet
import dk.binumre.model.SortBy
import dk.binumre.model.Stage
import dk.binumre.startlist.NameFormat
import java.time.ZoneId

val palette = listOf(
    "#FFD400", "#EE6A1B", "#EF4444", "#EC4899", "#A855F7", "#3B82F6", "#5BA4CF", "#14B8A6",
    "#22C55E", "#0F7A3B", "#92400E", "#FFFFFF", "#D4D4D4", "#737373", "#1E3A8A", "#000000",
)
private val hexColor = Regex("^#[0-9A-Fa-f]{6}$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupTab(state: AppState, dialogs: FileDialogs) {
    val s = state.strings
    val p = state.project
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(scroll).padding(24.dp).widthIn(max = 820.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(s["setup.general"]) {
                Label(s["setup.template"])
                SingleChoiceSegmentedButtonRow {
                    BibType.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = p.type == t, onClick = { state.setType(t) },
                            shape = SegmentedButtonDefaults.itemShape(i, BibType.entries.size),
                        ) { Text(s["type.${t.name}"]) }
                    }
                }
                OutlinedTextField(
                    p.eventName, { state.project = p.copy(eventName = it) },
                    label = { Text(s["setup.eventName"]) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Dropdown(s["setup.language"], p.language, BibTexts.languages, { Strings.languageName(it) + " ($it)" }) {
                        state.project = p.copy(language = it)
                    }
                    val system = ZoneId.systemDefault().id
                    val zones = remember(p.zone) {
                        (listOf("", "UTC") + ZoneId.getAvailableZoneIds().filter { it.startsWith("Europe/") }.sorted() + p.zone).distinct()
                    }
                    Dropdown(s["setup.zone"], p.zone, zones, { it.ifEmpty { s.format("setup.zone.system", system) } }) {
                        state.project = p.copy(zone = it)
                    }
                }
                RadioRow(s["setup.nameFormat"], p.nameFormat, NameFormat.entries, { s["name.${it.name}"] }) {
                    state.project = p.copy(nameFormat = it)
                }
            }

            if (p.type == BibType.MULTIDAY) {
                Section(s["setup.stages"]) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(s["setup.stageCount"], Modifier.width(160.dp))
                        OutlinedIconButton(onClick = { state.setStageCount(p.stageCount - 1) }, enabled = p.stageCount > 1) { Text("−") }
                        Text("${p.stageCount}", Modifier.width(32.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                        OutlinedIconButton(onClick = { state.setStageCount(p.stageCount + 1) }, enabled = p.stageCount < AppState.MAX_STAGES) { Text("+") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(p.sharedStartList, { state.setShared(it) })
                        Column {
                            Text(s["setup.shared"], fontWeight = FontWeight.SemiBold)
                            Text(s["setup.sharedHint"], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    for (i in 0 until p.stageCount) StageFields(state, i, true)
                }
            } else {
                Section(s["setup.distances"]) { StageFields(state, 0, false) }
            }

            if (p.type == BibType.RELAY) Section(s["setup.legColors"]) { LegColors(state) }

            Section(s["graphics.title"]) {
                Text(s["graphics.hint"], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GraphicButtons(state, dialogs)
                GraphicList(state, withFields = false)
            }

            Section(s["setup.print"]) {
                RadioRow(s["setup.sheet"], p.sheet, Sheet.entries, { s["sheet.${it.name}"] }) { state.project = p.copy(sheet = it) }
                RadioRow(s["setup.sort"], p.sortBy, SortBy.entries, { s["sort.${it.name}"] }) { state.project = p.copy(sortBy = it) }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(4.dp, 18.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

/** Overskrift (flerdagesløb) og afstand til start pr. startsted for etape [index]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageFields(state: AppState, index: Int, multiday: Boolean) {
    val s = state.strings
    val stage = state.project.stage(index)
    val list = state.lists.getOrNull(if (state.project.sharedStartList) 0 else index)
    fun change(f: (Stage) -> Stage) {
        state.project = TableLogic.withStage(state.project, index, f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (multiday) {
            Text(s.format("list.stage", index + 1) + (list?.let { " – " + it.source } ?: ""), style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (multiday) {
                OutlinedTextField(
                    stage.label, { v -> change { it.copy(label = v) } }, label = { Text(s["setup.stageLabel"]) },
                    placeholder = { Text(s.format("list.stage", index + 1)) }, singleLine = true, modifier = Modifier.width(220.dp),
                )
            }
            val names = (TableLogic.startNames(list) + stage.distances.keys.filter { it.isNotEmpty() }).distinct()
            for (name in names) {
                OutlinedTextField(
                    stage.distances[name].orEmpty(), { v -> change { TableLogic.withDistance(it, name, v) } },
                    label = { Text(s.format("setup.distance", name)) }, placeholder = { Text(s["setup.distanceHint"]) },
                    singleLine = true, modifier = Modifier.width(220.dp),
                )
            }
            OutlinedTextField(
                stage.distances[""].orEmpty(), { v -> change { TableLogic.withDistance(it, "", v) } },
                label = { Text(s[if (names.isEmpty()) "setup.distanceAll" else "setup.distanceOther"], maxLines = 1) },
                placeholder = { Text(s["setup.distanceHint"]) }, singleLine = true, modifier = Modifier.width(260.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegColors(state: AppState) {
    val s = state.strings
    val colors = state.project.legColors
    fun set(list: List<String>) {
        state.project = state.project.copy(legColors = list)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEachIndexed { i, hex ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.format("setup.leg", i + 1), style = MaterialTheme.typography.labelMedium)
                ColorPicker(hex, s["setup.color"], size = 44) { c -> if (c != null) set(colors.mapIndexed { j, old -> if (j == i) c else old }) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { set(colors + BibProject.DEFAULT_LEG_COLORS[colors.size % BibProject.DEFAULT_LEG_COLORS.size]) }) {
            Text(s["setup.addLeg"])
        }
        OutlinedButton(enabled = colors.size > 1, onClick = { set(colors.dropLast(1)) }) { Text(s["setup.removeLeg"]) }
    }
}

/** Farvefelt der åbner en palet og et felt til #RRGGBB. [hex] null = ingen farve (vises ternet). */
@Composable
fun ColorPicker(hex: String?, label: String, size: Int = 32, allowNone: String? = null, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Swatch(hex, size) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in palette.chunked(8)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in row) Swatch(c, 28) { onPick(c); open = false }
                }
                if (allowNone != null) {
                    Row(Modifier.clickable { onPick(null); open = false }, verticalAlignment = Alignment.CenterVertically) {
                        Swatch(null, 28) { onPick(null); open = false }
                        Spacer(Modifier.width(8.dp))
                        Text(allowNone)
                    }
                }
                var text by remember { mutableStateOf(hex.orEmpty()) }
                OutlinedTextField(
                    text,
                    { t ->
                        text = t
                        if (hexColor.matches(t.trim())) onPick(t.trim().uppercase())
                    },
                    label = { Text(label) }, isError = text.isNotEmpty() && !hexColor.matches(text.trim()), singleLine = true,
                    modifier = Modifier.width(266.dp),
                )
            }
        }
    }
}

@Composable
private fun Swatch(hex: String?, size: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.size(size.dp).clip(shape)
            .background(if (hex == null) Color.White else Color(0xFF000000 or BibProject.parseColor(hex).toLong()))
            .border(1.dp, Color(0x55000000), shape).clickable(onClick = onClick),
    ) {
        if (hex == null) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawLine(Color(0xFFC62828), androidx.compose.ui.geometry.Offset(0f, this.size.height), androidx.compose.ui.geometry.Offset(this.size.width, 0f), 2f)
        }
    }
}
