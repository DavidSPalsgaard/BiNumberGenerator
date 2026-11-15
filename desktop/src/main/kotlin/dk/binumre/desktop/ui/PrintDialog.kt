package dk.binumre.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dk.binumre.desktop.Printer
import dk.binumre.model.Sheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Udskriv eller gem som PDF: hvilke startnumre (alle, kun ikke udskrevne, én løber) og arkformat.
 * "Udskriv" åbner styresystemets printerdialog og sender siderne direkte til printeren.
 */
@Composable
fun PrintDialog(state: AppState, dialogs: FileDialogs, initial: PrintScope, onClose: () -> Unit) {
    val s = state.strings
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(initial) }
    var mark by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val bibs = state.bibsFor(choice)

    fun done(@Suppress("UNUSED_PARAMETER") count: Int) {
        if (mark) state.markPrinted(state.bibsFor(choice))
        onClose()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        icon = { Icon(AppIcons.Print, null) },
        title = { Text(s["print.title"]) },
        text = {
            Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s["print.which"], style = MaterialTheme.typography.labelLarge)
                Option(choice == PrintScope.All, s.format("print.all", state.bibSet.bibs.size)) { choice = PrintScope.All }
                val fresh = state.notPrinted.size
                if (state.project.printed.isNotEmpty()) {
                    Option(choice == PrintScope.NotPrinted, s.format("print.notPrinted", fresh), enabled = fresh > 0) { choice = PrintScope.NotPrinted }
                }
                (initial as? PrintScope.One)?.let { one ->
                    val bib = state.bibSet.bibs.firstOrNull { it.key == one.key }
                    val label = bib?.let { "${it.number}  ${it.displayName(state.project.nameFormat)}" + if (it.leg.isNotEmpty()) " (${s.format("setup.leg", it.leg)})" else "" } ?: one.key
                    Option(choice == one, s.format("print.one", label)) { choice = one }
                }
                HorizontalDivider(Modifier.padding4())
                Text(s["setup.sheet"], style = MaterialTheme.typography.labelLarge)
                for (sheet in Sheet.entries) {
                    Option(state.project.sheet == sheet, s["sheet.${sheet.name}"]) { state.project = state.project.copy(sheet = sheet) }
                }
                HorizontalDivider(Modifier.padding4())
                Row(Modifier.clickable { mark = !mark }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(mark, { mark = it })
                    Text(s["print.mark"])
                }
                if (state.project.printed.isNotEmpty()) {
                    TextButton(onClick = { state.forgetPrinted(); choice = PrintScope.All }) { Text(s.format("print.forget", state.project.printed.size)) }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = onClose) { Text(s["cancel"]) }
                OutlinedButton(enabled = !busy && bibs.isNotEmpty(), onClick = {
                    dialogs.save(s["dialog.pdf"], state.lastPdf?.name ?: suggestedName(state, "pdf"), "pdf")?.let { file ->
                        state.writePdf(file, bibs)
                        if (state.error == null) done(bibs.size)
                    }
                }) { Icon(AppIcons.Pdf, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s["print.pdf"]) }
                Button(enabled = !busy && bibs.isNotEmpty(), onClick = {
                    if (!Printer.available()) {
                        state.error = s["print.noPrinter"]
                        return@Button
                    }
                    val job = try {
                        Printer.prepare(bibs, state.project, s.format("print.job", state.project.eventName.ifBlank { "BiNumreGenerator" }))
                    } catch (e: Exception) {
                        state.error = s.format("print.error", e.message ?: e.toString())
                        null
                    } ?: return@Button
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { job.print() } }
                        busy = false
                        result.onSuccess {
                            state.message = s.format("msg.printed", bibs.size, job.printerName)
                            done(bibs.size)
                        }.onFailure { state.error = s.format("print.error", it.message ?: it.toString()) }
                    }
                }) {
                    Icon(AppIcons.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (busy) s["print.busy"] else s.format("print.print", bibs.size))
                }
            }
        },
    )
}

private fun Modifier.padding4() = this.padding(vertical = 6.dp)

@Composable
private fun Option(selected: Boolean, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.selectable(selected, enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick, enabled = enabled)
        Text(text, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}
