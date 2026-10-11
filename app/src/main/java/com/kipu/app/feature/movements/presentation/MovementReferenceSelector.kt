package com.kipu.app.feature.movements.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search

/** Edits only the containing sheet's draft; searching never changes selected IDs. */
@Composable
internal fun ReferenceSelector(
    title: String,
    options: List<MovementReferenceOption>,
    selected: Set<String>,
    enabled: Boolean,
    icon: ImageVector? = null,
    displayTitle: String? = null,
    emptySummary: String? = null,
    onLockedClick: (() -> Unit)? = null,
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
        firstSelected == null -> emptySummary ?: stringResource(R.string.history_filter_no_selection)
        selected.size == 1 -> firstSelected
        else -> stringResource(R.string.history_filter_selection_more, firstSelected, selected.size - 1)
    }

    Surface(
        onClick = {
            if (enabled) {
                search = ""
                open = true
            } else {
                onLockedClick?.invoke()
            }
        },
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("btn_reference_$title")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF0F766E),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = displayTitle ?: title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                val isCustomSelected = selected.isNotEmpty()
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isCustomSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp
                    ),
                    color = if (isCustomSelected) Color(0xFF0F766E) else Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!enabled) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = if (isCustomSelected) Color(0xFF0F766E) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    LaunchedEffect(enabled) { if (!enabled) open = false }
    if (open && enabled) {
        val visible = remember(catalog, search) { catalog.filter { it.label.contains(search.trim(), ignoreCase = true) } }
        AlertDialog(
            onDismissRequest = { open = false },
            title = {
                Text(
                    displayTitle ?: title,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                        },
                        label = { Text(stringResource(R.string.history_filter_search)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0F766E),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("input_reference_search")
                    )
                    Text(
                        stringResource(R.string.history_filter_selected, selected.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF0F766E),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (visible.isEmpty()) {
                        Text(
                            stringResource(if (catalog.isEmpty()) R.string.history_filter_none else R.string.history_filter_no_matches),
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color(0xFF64748B)
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp).testTag("reference_options")) {
                            items(visible, key = { it.id }) { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("reference_option_${option.id}")
                                        .toggleable(
                                            value = option.id in selected,
                                            role = Role.Checkbox,
                                            onValueChange = { onChange(if (option.id in selected) selected - option.id else selected + option.id) }
                                        ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = option.id in selected,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0F766E))
                                    )
                                    Text(option.label, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        stringResource(R.string.history_filter_done),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F766E)
                    )
                }
            }
        )
    }
}
