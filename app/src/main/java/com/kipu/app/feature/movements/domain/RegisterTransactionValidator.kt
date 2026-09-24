package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.RegisterTransactionCommand

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val field: String?, val message: String) : ValidationResult
}

class RegisterTransactionValidator {

    fun validate(command: RegisterTransactionCommand): ValidationResult {
        if (command.amountMinor <= 0) {
            return ValidationResult.Invalid("amount", "El monto debe ser mayor a cero")
        }
        if (command.currency.isBlank()) {
            return ValidationResult.Invalid("currency", "La moneda es requerida")
        }
        if (command.sourceAccountId.isNullOrBlank()) {
            val fieldName = if (command.type == MovementType.INCOME) "destination_account" else "source_account"
            return ValidationResult.Invalid(fieldName, "La cuenta es obligatoria")
        }
        if (command.merchantId != null && command.merchantProvisionalText != null) {
            return ValidationResult.Invalid("merchant", "Selecciona un comercio o escribe un nombre provisional")
        }
        if (command.merchantProvisionalText != null && command.merchantProvisionalText.trim().isEmpty()) {
            return ValidationResult.Invalid("merchant", "El nombre del comercio no puede estar vacío")
        }

        when (command.type) {
            MovementType.EXPENSE -> {
                if (command.categoryId.isNullOrBlank()) {
                    return ValidationResult.Invalid("category", "La categoría es obligatoria para un gasto")
                }
            }
            MovementType.INCOME -> {
                // Category is optional for income in Sprint 2
            }
            MovementType.TRANSFER -> {
                if (command.categoryId != null) {
                    return ValidationResult.Invalid("category", "Las transferencias no admiten categoría")
                }
                if (command.destinationAccountId.isNullOrBlank()) {
                    return ValidationResult.Invalid("destination_account", "La cuenta de destino es obligatoria para una transferencia")
                }
                if (command.sourceAccountId == command.destinationAccountId) {
                    return ValidationResult.Invalid("destination_account", "La cuenta de origen y destino no pueden ser la misma")
                }
            }
        }

        return ValidationResult.Valid
    }
}
