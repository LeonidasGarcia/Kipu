package com.kipu.app.core.database

interface DatabaseTransactionRunner {
    suspend operator fun <R> invoke(block: suspend () -> R): R
}
