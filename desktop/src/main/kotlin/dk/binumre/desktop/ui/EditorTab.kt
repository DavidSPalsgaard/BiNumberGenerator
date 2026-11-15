package dk.binumre.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dk.binumre.desktop.SkiaBibCanvas
import dk.binumre.layout.Align
import dk.binumre.layout.BibLayout
import dk.binumre.layout.GraphicEdit
import dk.binumre.model.BibElement
import dk.binumre.model.BibProject
import dk.binumre.model.BibType
import dk.binumre.model.ElementKind
import dk.binumre.model.Frame
import dk.binumre.model.InfoField
import java.io.File

private val selectionColor = Color(0xFF2563EB)

/** Elementer der giver mening for skabelonen (stafettens bånd, tur og hold kun ved stafet). */
private fun relevant(kind: ElementKind, type: BibType) =
    type == BibType.RELAY || kind !in setOf(ElementKind.LEG_BAND, ElementKind.LEG_STRIPE, ElementKind.LEG, ElementKind.TEAM)

private fun textKind(kind: ElementKind) = kind !in setOf(ElementKind.LEG_STRIPE, ElementKind.FOLD, ElementKind.INFO)

@Composable
fun EditorTab(state: AppState, dialogs: FileDialogs) {
    var showFrames by remember { mutableStateOf(true) }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFE9E3DC))) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                val ratio = (BibLayout.WIDTH / BibLayout.HEIGHT).toFloat()
                val width = if (maxWidth / ratio <= maxHeight) maxWidth else maxHeight * ratio
                Preview(state, showFrames, Modifier.size(width, width / ratio))
            }
            Navigation(state, showFrames) { showFrames = it }
        }
        Panel(state, dialogs)
    }
}

/** Det der kan vælges og trækkes: synlige elementer og grafik, med deres rammer. */
private fun items(state: AppState): List<Pair<Selection, Frame>> =
    state.elements.withIndex().filter { (_, e) -> e.visible && relevant(e.kind, state.project.type) }
        .map { (i, e) -> Selection(false, i) to e.frame } +
        state.project.graphics.mapIndexed { i, g -> Selection(true, i) to g.frame }

/** Startnummeret tegnet med [BibLayout.draw] – samme kode som PDF'en – med rammer om elementerne. */
@Composable
private fun Preview(state: AppState, showFrames: Boolean, modifier: Modifier) {
    Canvas(
        modifier.shadow(8.dp, RoundedCornerShape(2.dp)).background(Color.White).clipToBounds().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val scale = size.width / BibLayout.WIDTH
                val list = items(state)
                val selected = list.indexOfFirst { it.first == state.selection }.takeIf { it >= 0 }
                val hit = GraphicEdit.hit(list.map { it.second }, down.position.x / scale, down.position.y / scale, selected, tolerance = 8.dp.toPx() / scale)
                state.selection = hit?.let { list[it.first].first }
                if (hit != null) {
                    val (sel, start) = list[hit.first]
                    drag(down.id) { change ->
                        val moved = change.position - down.position
                        val frame = GraphicEdit.drag(start, hit.second, moved.x / scale, moved.y / scale)
                        if (sel.graphic) state.updateGraphic(sel.index, state.project.graphics[sel.index].moved(frame))
                        else state.updateElement(sel.index, state.elements[sel.index].moved(frame))
                        change.consume()
                    }
                }
            }
        },
    ) {
        val scale = size.width / BibLayout.WIDTH.toFloat()
        drawIntoCanvas { BibLayout.draw(SkiaBibCanvas(it.nativeCanvas, scale, state.images::get), state.previewBib, state.project, state.zone) }
        fun rect(f: Frame) = Offset((f.x * scale).toFloat(), (f.y * scale).toFloat()) to Size((f.width * scale).toFloat(), (f.height * scale).toFloat())
        val list = items(state)
        if (showFrames) for ((_, f) in list) {
            val (o, sz) = rect(f)
            drawRect(selectionColor.copy(alpha = 0.35f), o, sz, style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
        }
        state.project.graphics.forEach { g ->
            if (state.images.get(g.file) == null) {
                val (o, sz) = rect(g.frame)
                drawRect(Color(0x33DC2626), o, sz)
                drawRect(Color(0xFFDC2626), o, sz, style = Stroke(1.dp.toPx()))
            }
        }
        list.firstOrNull { it.first == state.selection }?.let { (_, f) -> selectionFrame(rect(f)) }
    }
}

private fun DrawScope.selectionFrame(r: Pair<Offset, Size>) {
    val (o, sz) = r
    drawRect(selectionColor, o, sz, style = Stroke(1.5.dp.toPx()))
    val h = 8.dp.toPx()
    for (corner in listOf(o, o + Offset(sz.width, 0f), o + Offset(0f, sz.height), o + Offset(sz.width, sz.height))) {
        drawRect(Color.White, corner - Offset(h / 2, h / 2), Size(h, h))
        drawRect(selectionColor, corner - Offset(h / 2, h / 2), Size(h, h), style = Stroke(1.5.dp.toPx()))
    }
}

/** Bladring mellem løberne og udskrift af den viste. */
@Composable
private fun Navigation(state: AppState, showFrames: Boolean, setShowFrames: (Boolean) -> Unit) {
    val s = state.strings
    val bibs = state.bibSet.bibs
    val index = state.previewIndex.coerceIn(0, maxOf(0, bibs.size - 1))
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(showFrames, { setShowFrames(!showFrames) }, label = { Text(s["editor.frames"]) })
        Spacer(Modifier.weight(1f))
        IconButton(enabled = index > 0, onClick = { state.previewIndex = index - 1 }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = s["editor.prev"])
        }
        val bib = bibs.getOrNull(index)
        Text(
            if (bib == null) s["editor.sample"]
            else s.format("editor.runner", index + 1, bibs.size) + "  ·  " +
                listOf(bib.number, bib.displayName(state.project.nameFormat), bib.cls).filter { it.isNotEmpty() }.joinToString("  "),
            Modifier.padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        IconButton(enabled = index < bibs.size - 1, onClick = { state.previewIndex = index + 1 }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = s["editor.next"])
        }
        Spacer(Modifier.weight(1f))
        TextButton(enabled = bib != null, onClick = { bib?.let { state.printRequest = PrintScope.One(it.key) } }) {
            Icon(AppIcons.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s["editor.printThis"])
        }
    }
}

@Composable
private fun Panel(state: AppState, dialogs: FileDialogs) {
    val s = state.strings
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.width(360.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(tab == 0, { tab = 0 }, text = { Text(s["editor.elements"]) })
            Tab(tab == 1, { tab = 1 }, text = { Text(s.format("editor.graphics", state.project.graphics.size)) })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == 0) ElementsPanel(state) else GraphicsPanel(state, dialogs)
        }
    }
}

private fun foldName(e: BibElement, s: Strings): String {
    val left = e.x + e.width / 2 < BibLayout.WIDTH / 2
    val top = e.y + e.height / 2 < BibLayout.HEIGHT / 2
    return s["kind.FOLD"] + " (" + s["corner.${if (top) "top" else "bottom"}${if (left) "Left" else "Right"}"] + ")"
}

private fun elementName(e: BibElement, s: Strings) = if (e.kind == ElementKind.FOLD) foldName(e, s) else s["kind.${e.kind.name}"]

@Composable
private fun ElementsPanel(state: AppState) {
    val s = state.strings
    val elements = state.elements
    Text(s["editor.hint"], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Det valgte elements egenskaber øverst, så de ses uden at rulle
    val sel = state.selection
    if (sel != null && !sel.graphic) state.elements.getOrNull(sel.index)?.let { e -> ElementProperties(state, sel.index, e); HorizontalDivider() }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)).padding(vertical = 4.dp)) {
            elements.forEachIndexed { i, e ->
                if (!relevant(e.kind, state.project.type)) return@forEachIndexed
                val selected = state.selection == Selection(false, i)
                Row(
                    Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { if (e.visible) state.selection = Selection(false, i) }.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(e.visible, { v ->
                        state.updateElement(i, e.copy(visible = v))
                        state.selection = if (v) Selection(false, i) else null
                    })
                    Spacer(Modifier.width(8.dp))
                    Text(elementName(e, s), fontWeight = if (selected) FontWeight.SemiBold else null)
                }
            }
        }
    }
    TextButton(enabled = state.project.customLayout, onClick = { state.resetLayout() }) {
        Icon(AppIcons.Restart, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s["editor.reset"])
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElementProperties(state: AppState, index: Int, e: BibElement) {
    val s = state.strings
    Text(elementName(e, s), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    FrameFields(s, e.frame) { state.updateElement(index, e.moved(it)) }
    if (textKind(e.kind) || e.kind == ElementKind.INFO) {
        if (textKind(e.kind)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Align.entries.forEachIndexed { i, a ->
                    SegmentedButton(e.align == a, { state.updateElement(index, e.copy(align = a)) }, SegmentedButtonDefaults.itemShape(i, Align.entries.size)) {
                        Text(s["align.${a.name}"])
                    }
                }
            }
        }
        Row(Modifier.clickable { state.updateElement(index, e.copy(rotated = !e.rotated)) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(e.rotated, { state.updateElement(index, e.copy(rotated = it)) })
            Text(s["editor.rotated"])
        }
    }
    if (e.kind != ElementKind.FOLD) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (e.kind != ElementKind.LEG_STRIPE && e.kind != ElementKind.LEG_BAND) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s["editor.textColor"], style = MaterialTheme.typography.labelMedium)
                ColorPicker(e.color?.let(BibProject::hex), s["setup.color"], allowNone = s["editor.standard"]) {
                    state.updateElement(index, e.copy(color = it?.let(BibProject::parseColor)))
                }
            }
        }
        if (e.kind != ElementKind.LEG_STRIPE && e.kind != ElementKind.LEG_BAND) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s["editor.background"], style = MaterialTheme.typography.labelMedium)
                ColorPicker(e.background?.let(BibProject::hex), s["setup.color"], allowNone = s["editor.none"]) {
                    state.updateElement(index, e.copy(background = it?.let(BibProject::parseColor)))
                }
            }
        }
    }
    if (e.kind == ElementKind.INFO) {
        Text(s["editor.infoFields"], style = MaterialTheme.typography.labelLarge)
        for (f in InfoField.entries) {
            val on = f in state.project.infoFields
            Row(Modifier.clickable { toggle(state, f) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(on, { toggle(state, f) })
                Text(s["info.${f.name}"])
            }
        }
    }
    if (e.kind == ElementKind.FOLD) Text(s["editor.foldHint"], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun toggle(state: AppState, f: InfoField) {
    val p = state.project
    state.project = p.copy(infoFields = if (f in p.infoFields) p.infoFields - f else p.infoFields + f)
}

/** X, Y, bredde og højde i mm – holdes inden for startnummeret. */
@Composable
private fun FrameFields(s: Strings, f: Frame, onChange: (Frame) -> Unit) {
    val gap = Arrangement.spacedBy(6.dp)
    Row(horizontalArrangement = gap) {
        NumberField(s["editor.x"], f.x, Modifier.weight(1f)) { onChange(GraphicEdit.clamp(f.copy(x = it))) }
        NumberField(s["editor.y"], f.y, Modifier.weight(1f)) { onChange(GraphicEdit.clamp(f.copy(y = it))) }
    }
    Row(horizontalArrangement = gap) {
        NumberField(s["editor.width"], f.width, Modifier.weight(1f)) { onChange(GraphicEdit.clamp(f.copy(width = it))) }
        NumberField(s["editor.height"], f.height, Modifier.weight(1f)) { onChange(GraphicEdit.clamp(f.copy(height = it))) }
    }
}

@Composable
private fun GraphicsPanel(state: AppState, dialogs: FileDialogs) {
    val s = state.strings
    FilledTonalButton(onClick = {
        dialogs.open(s["dialog.image"], listOf("png", "jpg", "jpeg"), multiple = true).forEach(state::addGraphic)
    }) { Icon(AppIcons.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(s["editor.add"]) }
    Text(s["editor.graphicsHint"], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    state.project.graphics.forEachIndexed { i, g ->
        val selected = state.selection == Selection(true, i)
        val shape = RoundedCornerShape(10.dp)
        Column(
            Modifier.fillMaxWidth()
                .border(if (selected) 2.dp else 1.dp, if (selected) selectionColor else MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable { state.selection = Selection(true, i) }.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(File(g.file).name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.images.get(g.file) == null) {
                        Text(s["editor.missing"], color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = { state.removeGraphic(i) }) { Icon(Icons.Default.Delete, contentDescription = s["remove"]) }
            }
            FrameFields(s, g.frame) { state.updateGraphic(i, g.moved(it)) }
        }
    }
    Spacer(Modifier.height(8.dp))
}
