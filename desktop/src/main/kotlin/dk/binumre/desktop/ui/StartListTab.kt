package dk.binumre.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dk.binumre.model.BibType
import dk.binumre.startlist.StartEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val startListExtensions = listOf("xml", "xlsx", "csv", "txt")

@Composable
fun StartListTab(state: AppState, dialogs: FileDialogs) {
    val s = state.strings
    var current by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf<String?>(null) }
    var onlyMissing by remember { mutableStateOf(false) }
    var onlyNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var ofeedSlot by remember { mutableStateOf<Int?>(null) }
    var refreshing by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    fun refresh(at: Int) {
        refreshing = at
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { state.refreshOFeed(at) } }
            refreshing = null
            result.onSuccess { state.setList(at, it) }.onFailure { state.error = it.message ?: it.toString() }
        }
    }

    val slots = state.slotCount
    val slot = current.coerceIn(0, slots - 1)
    val list = state.lists.getOrNull(slot)
    if (cls != null && list?.classes?.contains(cls) != true) cls = null
    fun load(at: Int) {
        state.loadFiles(dialogs.open(s["dialog.startlist"], startListExtensions, multiple = slots > 1), at)
        current = at
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Pladser til startlister: én, eller én pr. etape
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 0 until slots) {
                SlotCard(
                    title = if (slots > 1) state.project.stage(i).label.ifEmpty { s.format("list.stage", i + 1) }
                    else s[if (state.project.type == BibType.MULTIDAY) "list.allStages" else "list.startlist"],
                    file = state.lists.getOrNull(i)?.let { "${it.source} · ${s.format("list.rows", it.entries.size)}" },
                    selected = i == slot && slots > 1,
                    emptyText = s["list.none"],
                    onSelect = { current = i },
                    onLoad = { load(i) },
                    onOFeed = { ofeedSlot = i },
                    onRefresh = if (state.project.ofeed.containsKey(i)) ({ refresh(i) }) else null,
                    refreshing = refreshing == i,
                    onRemove = { state.removeList(i) },
                    s = s,
                )
            }
        }

        if (list == null) {
            EmptyState(state, onLoad = { load(slot) })
        } else {
            val entries = list.entries
            val type = state.project.type
            val printed = state.project.printed
            // Ved flerdagesløb kan nummeret stå på en anden etape: brug løberens nummer fra startnumrene
            val bibs = state.bibSet.bibs
            val numbers = remember(entries, bibs, type) {
                if (type == BibType.MULTIDAY) entries.map { TableLogic.resolvedNumber(it, bibs) } else entries.map { it.bib.trim() }
            }
            val shown = remember(entries, numbers, query, cls, onlyMissing, onlyNew, printed) {
                TableLogic.filter(entries, query, cls, false).filter { i ->
                    (!onlyMissing || numbers[i].isEmpty()) && (!onlyNew || TableLogic.entryKey(entries[i], type, numbers[i]) !in printed)
                }
            }
            val relay = type == BibType.RELAY
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text(s["list.search"], maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.width(280.dp),
                    shape = RoundedCornerShape(24.dp),
                )
                Dropdown("", cls, listOf<String?>(null) + list.classes, { it ?: s["list.allClasses"] }) { cls = it }
                FilterChip(onlyMissing, { onlyMissing = !onlyMissing }, label = { Text(s["list.onlyMissing"]) })
                if (printed.isNotEmpty()) FilterChip(onlyNew, { onlyNew = !onlyNew }, label = { Text(s["list.onlyNew"]) })
                Spacer(Modifier.weight(1f))
                val missing = entries.indices.count { numbers[it].isEmpty() && !entries[it].excluded }
                Text(
                    s.format("list.summary", shown.size, entries.size, entries.count { !it.excluded }, missing),
                    color = if (missing > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    val included = shown.count { !entries[it].excluded }
                    val shownSet = remember(shown) { shown.toHashSet() }
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .padding(start = 8.dp, end = 18.dp).height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(
                            state = when (included) {
                                0 -> ToggleableState.Off
                                shown.size -> ToggleableState.On
                                else -> ToggleableState.Indeterminate
                            },
                            onClick = {
                                val exclude = included == shown.size
                                state.updateEntries(slot) { i, e -> if (i in shownSet) e.copy(excluded = exclude) else e }
                            },
                            modifier = Modifier.width(40.dp),
                        )
                        for ((key, weight) in columns(relay)) Cell(s[key], weight, bold = true)
                        Spacer(Modifier.width(80.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        val scroll = rememberLazyListState()
                        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = scroll) {
                            items(shown, key = { it }) { i ->
                                val e = entries[i]
                                val number = numbers[i]
                                val key = TableLogic.entryKey(e, type, number)
                                EntryRow(
                                    state, e, number, relay, isNew = printed.isNotEmpty() && key.isNotEmpty() && key !in printed,
                                    onToggle = { on -> state.updateEntries(slot) { j, x -> if (j == i) x.copy(excluded = !on) else x } },
                                    onEdit = { editing = i },
                                    onPrint = if (number.isNotEmpty() && !e.excluded) ({ state.printRequest = PrintScope.One(key) }) else null,
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd))
                    }
                }
            }
        }
    }

    val edit = editing
    if (edit != null && list != null && edit in list.entries.indices) {
        EditDialog(state, list.entries[edit], state.dateOf(slot), state.project.type == BibType.RELAY, onClose = { editing = null }) { changed ->
            state.updateEntries(slot) { j, x -> if (j == edit) changed else x }
            editing = null
        }
    }
    ofeedSlot?.let { at -> OFeedDialog(state, at) { ofeedSlot = null; current = at } }
}

@Composable
private fun SlotCard(
    title: String, file: String?, selected: Boolean, emptyText: String,
    onSelect: () -> Unit, onLoad: () -> Unit, onOFeed: () -> Unit, onRefresh: (() -> Unit)?, refreshing: Boolean, onRemove: () -> Unit, s: Strings,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.widthIn(min = 240.dp, max = 320.dp)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onSelect).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (file != null) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    IconButton(onClick = onRemove, Modifier.size(24.dp)) { Icon(Icons.Default.Close, s["remove"], Modifier.size(16.dp)) }
                }
            }
        }
        Text(
            file ?: emptyText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (file == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = onLoad, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                Icon(AppIcons.Upload, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(s["list.file"])
            }
            if (onRefresh != null) {
                FilledTonalButton(onClick = onRefresh, enabled = !refreshing, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(s[if (refreshing) "ofeed.fetching" else "list.refresh"])
                }
            }
            TextButton(onClick = onOFeed) {
                Icon(AppIcons.Cloud, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("OFeed")
            }
        }
    }
}

@Composable
private fun EmptyState(state: AppState, onLoad: () -> Unit) {
    val s = state.strings
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MiniBib("?")
            Text(s["list.emptyTitle"], style = MaterialTheme.typography.titleLarge)
            Text(s["list.empty"], Modifier.widthIn(max = 520.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Button(onClick = onLoad) { Icon(AppIcons.Upload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(s["list.load"]) }
        }
    }
}

private fun columns(relay: Boolean): List<Pair<String, Float>> = listOf(
    "col.bib" to 0.7f, "col.name" to 2.2f, "col.club" to 1.8f, "col.class" to 0.9f, "col.card" to 1f,
    "col.start" to 0.9f, "col.course" to 0.7f, "col.startName" to 0.7f,
) + if (relay) listOf("col.team" to 1.6f, "col.leg" to 0.5f) else emptyList()

@Composable
private fun RowScope.Cell(text: String, weight: Float, bold: Boolean = false, dim: Boolean = false) {
    Text(
        text, Modifier.weight(weight).padding(horizontal = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = if (dim) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (bold) FontWeight.Bold else null, style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun EntryRow(state: AppState, e: StartEntry, number: String, relay: Boolean, isNew: Boolean, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onPrint: (() -> Unit)?) {
    val missing = number.isEmpty() && !e.excluded
    Row(
        Modifier.fillMaxWidth()
            .background(if (missing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onEdit).padding(horizontal = 8.dp).height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(!e.excluded, onToggle, Modifier.width(40.dp))
        val values = listOf(
            number, e.displayName(state.project.nameFormat), e.club, e.cls, e.card?.toString().orEmpty(),
            TableLogic.time(e.start, state.zone), e.course, e.startName,
        ) + if (relay) listOf(e.teamName, e.leg) else emptyList()
        columns(relay).forEachIndexed { i, (_, weight) ->
            if (i == 0 && isNew) {
                Row(Modifier.weight(weight).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(values[0], fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(4.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text(state.strings["list.new"]) }
                }
            } else Cell(values[i], weight, bold = i == 0, dim = e.excluded || (i == 0 && e.bib.isEmpty()))
        }
        Box(Modifier.width(40.dp)) {
            if (onPrint != null) IconButton(onClick = onPrint) {
                Icon(AppIcons.Print, contentDescription = state.strings["list.printOne"], tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.width(40.dp)) {
            Icon(Icons.Default.Edit, contentDescription = state.strings["list.edit"], tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EditDialog(state: AppState, entry: StartEntry, date: java.time.LocalDate, relay: Boolean, onClose: () -> Unit, onSave: (StartEntry) -> Unit) {
    val s = state.strings
    var f by remember { mutableStateOf(TableLogic.fields(entry, state.zone)) }
    var badField by remember { mutableStateOf<String?>(null) }

    @Composable
    fun RowScope.Field(key: String, value: String, weight: Float = 1f, error: Boolean = false, modifier: Modifier = Modifier, set: (String) -> Unit) {
        OutlinedTextField(value, set, label = { Text(s[key], maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true, isError = error, modifier = modifier.weight(weight))
    }
    // Startnummeret er det man oftest retter: markøren står der fra start
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(s["edit.title"]) },
        text = {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val gap = Arrangement.spacedBy(8.dp)
                Row(horizontalArrangement = gap) {
                    Field("edit.bib", f.bib, modifier = Modifier.focusRequester(focus)) { f = f.copy(bib = it) }
                    Field("col.class", f.cls) { f = f.copy(cls = it) }
                    Field("col.card", f.card, error = badField == "card") { f = f.copy(card = it) }
                }
                Row(horizontalArrangement = gap) {
                    Field("edit.given", f.given) { f = f.copy(given = it) }
                    Field("edit.family", f.family) { f = f.copy(family = it) }
                }
                if (f.name.isNotEmpty() || (entry.given.isEmpty() && entry.family.isEmpty())) {
                    Row { Field("edit.fullName", f.name) { f = f.copy(name = it) } }
                }
                Row { Field("col.club", f.club) { f = f.copy(club = it) } }
                Row(horizontalArrangement = gap) {
                    Field("edit.start", f.start, weight = 2f, error = badField == "start") { f = f.copy(start = it) }
                    Field("col.course", f.course) { f = f.copy(course = it) }
                    Field("col.startName", f.startName) { f = f.copy(startName = it) }
                }
                if (relay) Row(horizontalArrangement = gap) {
                    Field("col.team", f.teamName, weight = 3f) { f = f.copy(teamName = it) }
                    Field("col.leg", f.leg) { f = f.copy(leg = it) }
                }
                badField?.let { Text(s["edit.error.$it"], color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    onSave(TableLogic.apply(entry, f, state.zone, date))
                } catch (e: FieldException) {
                    badField = e.field
                }
            }) { Text(s["ok"]) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(s["cancel"]) } },
    )
}

@Composable
private fun OFeedDialog(state: AppState, slot: Int, onClose: () -> Unit) {
    val s = state.strings
    val scope = rememberCoroutineScope()
    val saved = state.project.ofeed[slot]
    var link by remember { mutableStateOf(saved?.link.orEmpty()) }
    var password by remember { mutableStateOf(saved?.password.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(s["ofeed.title"]) },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(link, { link = it }, label = { Text(s["ofeed.link"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text(s["ofeed.password"]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = !busy && link.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    val source = dk.binumre.model.OFeedSource(link.trim(), password)
                    val result = withContext(Dispatchers.IO) { runCatching { state.fetchOFeed(source) } }
                    busy = false
                    result.onSuccess { state.addOFeed(it, slot, source); onClose() }
                        .onFailure { state.error = it.message ?: it.toString() }
                }
            }) { Text(s[if (busy) "ofeed.fetching" else "ofeed.fetch"]) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(s["cancel"]) } },
    )
}
