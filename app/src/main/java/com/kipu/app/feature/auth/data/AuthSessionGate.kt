package com.kipu.app.feature.auth.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Session restoration and recovery must never import different sessions concurrently. */
@Singleton
class AuthSessionGate @Inject constructor() {
    private val mutex = Mutex()
    suspend fun <T> exclusive(operation: suspend () -> T): T = mutex.withLock {
        val result = operation()
        currentCoroutineContext().ensureActive()
        result
    }
}
