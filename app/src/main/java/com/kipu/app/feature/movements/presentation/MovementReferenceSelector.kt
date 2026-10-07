package com.kipu.app.feature.movements.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kipu.app.R

/** Edits only the containing sheet's draft; searching never changes selected IDs. */
@Composable
internal fun ReferenceSelector(
    title: String,
    options: List<MovementReferenceOption>,
    selected: Set<String>,
    enabled: Boolean,
    onChange: (Set<String>) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val historicalLabel = stringResource(R.string.history_filter_historical_reference)
    val names = remember(options) { options.associate { it.id to it.label } }
    val catalog = remember(options, selected, historicalLabel) {
        (options.distinctBy { it.id } + selected.filter { it !in names }
            .map { MovementReferenceOption(it, historicalLabel) })
            .sortedWith(compareBy<MovementReferenceOption> { it.label.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
    }
    val firstSelected = catalog.firstOrNull { it.id in selected }?.label
    val summary = when {
        firstSelected == null -> stringResource(R.string.history_filter_no_selection)
        selected.size == 1 -> firstSelected
        else -> stringResource(R.string.history_filter_selection_more, firstSelected, selected.size - 1)
    }
    OutlinedButton(onClick = { search = ""; open = true }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("btn_reference_$title")) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    if (open && enabled) {
        val visible = remember(catalog, search) { catalog.filter { it.label.contains(search.trim(), ignoreCase = true) } }
        AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(search, { search = it }, singleLine = true,
                    label = { Text(stringResource(R.string.history_filter_search)) },
                    modifier = Modifier.fillMaxWidth().testTag("input_reference_search"))
                Text(stringResource(R.string.history_filter_selected, selected.size),
                    style = MaterialTheme.typography.labelMedium)
                if (visible.isEmpty()) {
                    Text(stringResource(if (catalog.isEmpty()) R.string.history_filter_none else R.string.history_filter_no_matches),
                        modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp).testTag("reference_options")) {
                        items(visible, key = { it.id }) { option ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("reference_option_${option.id}")
                                .toggleable(value = option.id in selected, role = Role.Checkbox,
                                    onValueChange = { onChange(if (option.id in selected) selected - option.id else selected + option.id) }),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = option.id in selected, onCheckedChange = null)
                                Text(option.label, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.history_filter_done)) } })
    }
}
