package dk.binumre.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Desktop
import java.io.File

/** Starter brugerfladen. [args]: startlister der indlæses med det samme, og evt. `--project <fil>`. */
fun runGui(args: List<String>) {
    val state = AppState()
    val files = mutableListOf<File>()
    var i = 0
    while (i < args.size) {
        if (args[i] == "--project") args.getOrNull(++i)?.let { state.openProject(File(it)) } else files += File(args[i])
        i++
    }
    if (files.isNotEmpty()) {
        if (state.screen == Screen.START) state.start(if (files.size > 1) dk.binumre.model.BibType.MULTIDAY else dk.binumre.model.BibType.SINGLE, files.size)
        state.loadFiles(files)
    }

    application {
        var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
        val name = state.projectFile?.name
        val title = listOfNotNull(name, "BiNumreGenerator").joinToString(" – ") +
            if (state.dirty && name != null) " (${state.strings["status.unsaved"]})" else ""
        Window(
            onCloseRequest = { if (state.dirty) confirm = ::exitApplication else exitApplication() },
            state = rememberWindowState(size = DpSize(1320.dp, 880.dp)),
            title = title,
        ) {
            val dialogs = remember { FileDialogs(state, window) }
            OTheme {
                App(state, dialogs, askDiscard = { action -> if (state.dirty) confirm = action else action() })
                confirm?.let { action ->
                    val s = state.strings
                    AlertDialog(
                        onDismissRequest = { confirm = null },
                        title = { Text(s["confirm.close.title"]) },
                        text = { Text(s["confirm.close.text"]) },
                        confirmButton = {
                            Button(onClick = {
                                confirm = null
                                if (saveProject(state, dialogs, false)) action()
                            }) { Text(s["confirm.save"]) }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = { confirm = null; action() }) { Text(s["confirm.discard"]) }
                                TextButton(onClick = { confirm = null }) { Text(s["cancel"]) }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Gemmer projektet (spørger om filnavn første gang eller ved [saveAs]). False når brugeren fortryder eller det fejler. */
fun saveProject(state: AppState, dialogs: FileDialogs, saveAs: Boolean): Boolean {
    val file = state.projectFile.takeIf { !saveAs }
        ?: dialogs.save(state.strings["dialog.saveProject"], state.projectFile?.name ?: suggestedName(state, "json"), "json")
        ?: return false
    state.saveProject(file)
    return !state.dirty
}

fun suggestedName(state: AppState, extension: String): String =
    (state.project.eventName.ifBlank { "startnumre" }.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifEmpty { "startnumre" }) + ".$extension"

@Composable
fun App(state: AppState, dialogs: FileDialogs, askDiscard: (() -> Unit) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.screen) {
            Screen.START -> StartScreen(state, dialogs)
            Screen.MAIN -> MainScreen(state, dialogs, askDiscard)
        }
    }
    state.printRequest?.let { PrintDialog(state, dialogs, it) { state.printRequest = null } }
    state.error?.let { text ->
        AlertDialog(
            onDismissRequest = { state.error = null },
            title = { Text(state.strings["error.title"]) },
            text = { Text(text) },
            confirmButton = { Button(onClick = { state.error = null }) { Text(state.strings["ok"]) } },
        )
    }
}

@Composable
private fun MainScreen(state: AppState, dialogs: FileDialogs, askDiscard: (() -> Unit) -> Unit) {
    val s = state.strings
    Row(Modifier.fillMaxSize()) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            header = {
                ControlFlag(40.dp, Modifier.padding(top = 12.dp, bottom = 8.dp))
            },
        ) {
            Spacer(Modifier.height(8.dp))
            val items = listOf(
                Triple(Page.LIST, "nav.list", Icons.AutoMirrored.Filled.List),
                Triple(Page.SETUP, "nav.setup", Icons.Default.Settings),
                Triple(Page.DESIGN, "nav.design", AppIcons.Design),
            )
            for ((page, key, icon) in items) {
                NavigationRailItem(
                    selected = state.page == page,
                    onClick = { state.page = page },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(s[key]) },
                )
            }
            Spacer(Modifier.weight(1f))
            NavigationRailItem(
                selected = false,
                onClick = { askDiscard { state.newProject() } },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                label = { Text(s["menu.new"]) },
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(state, dialogs)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.page) {
                    Page.LIST -> StartListTab(state, dialogs)
                    Page.SETUP -> SetupTab(state)
                    Page.DESIGN -> EditorTab(state, dialogs)
                }
            }
            StatusBar(state)
        }
    }
}

@Composable
private fun TopBar(state: AppState, dialogs: FileDialogs) {
    val s = state.strings
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                state.project.eventName.ifBlank { s["top.unnamed"] }, style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val type = s["type.${state.project.type.name}"] +
                    if (state.project.type == dk.binumre.model.BibType.MULTIDAY) " · " + s.format("top.stages", state.project.stageCount) else ""
                Text(type, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                state.projectFile?.let {
                    Text("· ${it.name}" + if (state.dirty) " (${s["status.unsaved"]})" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = { dialogs.open(s["dialog.openProject"], listOf("json")).firstOrNull()?.let(state::openProject) }) {
            Icon(AppIcons.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s["menu.open"])
        }
        TextButton(onClick = { saveProject(state, dialogs, false) }) {
            Icon(AppIcons.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s["menu.save"])
        }
        TextButton(onClick = { saveProject(state, dialogs, true) }) { Text(s["menu.saveAs"]) }
        LanguageMenu(state)
        Spacer(Modifier.width(4.dp))
        OutlinedButton(onClick = { state.printRequest = PrintScope.All }) {
            Icon(AppIcons.Pdf, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s["menu.pdf"])
        }
        Button(onClick = { state.printRequest = if (state.project.printed.isNotEmpty() && state.notPrinted.isNotEmpty()) PrintScope.NotPrinted else PrintScope.All }) {
            Icon(AppIcons.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(s["menu.print"])
        }
    }
}

@Composable
fun LanguageMenu(state: AppState) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(Strings.languageName(state.strings.language)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (code in Strings.available) {
                DropdownMenuItem(text = { Text(Strings.languageName(code)) }, onClick = { state.setLanguage(code); open = false })
            }
        }
    }
}

@Composable
private fun StatusBar(state: AppState) {
    val s = state.strings
    val set = state.bibSet
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.message, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        state.lastPdf?.let { pdf ->
            if (Desktop.isDesktopSupported()) {
                androidx.compose.material3.TextButton(onClick = { runCatching { Desktop.getDesktop().open(pdf) }.onFailure { state.error = it.message } }) {
                    Text(s["status.showPdf"])
                }
            }
        }
        if (set.withoutNumber > 0) {
            Text(s.format("status.missing", set.withoutNumber), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        val fresh = state.notPrinted.size
        if (state.project.printed.isNotEmpty() && fresh > 0) {
            AssistChip(onClick = { state.printRequest = PrintScope.NotPrinted }, label = { Text(s.format("status.new", fresh)) })
        }
        Text(s.format("status.bibs", set.bibs.size), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}
