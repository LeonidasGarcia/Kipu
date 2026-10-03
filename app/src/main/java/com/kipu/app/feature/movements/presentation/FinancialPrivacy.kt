package com.kipu.app.feature.movements.presentation

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.AnnotatedString

/** Replace raw EditText semantics while keeping accessible text entry available. */
internal fun Modifier.privateAmount(masked: Boolean, enabled: Boolean, label: String = "Importe oculto", onChange: (String) -> Unit): Modifier =
    if (!masked) this else clearAndSetSemantics {
        contentDescription = label
        editableText = AnnotatedString("••••••")
        password()
        if (!enabled) disabled()
        if (enabled) setText { value -> onChange(value.text); true }
    }
