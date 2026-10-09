package com.kipu.app.feature.debts.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DebtDateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtDatePickerField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    pickerTag: String,
    calendarTag: String,
    confirmTag: String,
    clearTag: String? = null,
    modifier: Modifier = Modifier,
) {
    var isPickerVisible by rememberSaveable(pickerTag) { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )

    LaunchedEffect(date) {
        pickerState.selectedDateMillis = date
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
    }

    OutlinedButton(
        onClick = { isPickerVisible = true },
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(pickerTag),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = date?.format(DebtDateFormatter) ?: "Seleccionar fecha",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (date == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(Icons.Default.CalendarToday, contentDescription = "Elegir fecha")
        }
    }

    if (isPickerVisible) {
        DatePickerDialog(
            onDismissRequest = { isPickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateChange(
                            pickerState.selectedDateMillis?.let { selectedMillis ->
                                Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate()
                            },
                        )
                        isPickerVisible = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                    modifier = Modifier.testTag(confirmTag),
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (clearTag != null && date != null) {
                        TextButton(
                            onClick = {
                                pickerState.selectedDateMillis = null
                                onDateChange(null)
                                isPickerVisible = false
                            },
                            modifier = Modifier.testTag(clearTag),
                        ) {
                            Text("Quitar")
                        }
                    }
                    TextButton(onClick = { isPickerVisible = false }) { Text("Cancelar") }
                }
            },
        ) {
            DatePicker(
                state = pickerState,
                modifier = Modifier.testTag(calendarTag),
                showModeToggle = false,
            )
        }
    }
}

internal fun parseDebtDate(value: String): LocalDate? =
    value.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

internal fun formatDebtDate(date: LocalDate): String = date.format(DebtDateFormatter)
