package com.kipu.app.feature.notifications.presentation

import com.kipu.app.feature.notifications.domain.AppNotification
import com.kipu.app.feature.notifications.domain.NotificationCategory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun AppNotification.expectedDueDateLabel(): String? {
    if (category != NotificationCategory.REMINDER) return null

    val rawDate = runCatching {
        Json.parseToJsonElement(eventPayloadJson)
            .jsonObject["due_date"]
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull() ?: return null
    val date = runCatching { LocalDate.parse(rawDate.take(10)) }.getOrNull() ?: return null
    val locale = Locale.forLanguageTag("es-PE")
    val fullDate = date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", locale))
    val alternateLocaleDate = date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es")))
    val bodyDateForms = listOf(
        rawDate,
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)),
        date.format(DateTimeFormatter.ofPattern("dd/MM", locale)),
        date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", locale)),
        alternateLocaleDate,
    )
    if (bodyDateForms.any { form -> body.contains(form, ignoreCase = true) }) return null

    return "Vence el $fullDate"
}
