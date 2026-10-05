package com.kipu.app.feature.auth.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuthSessionGateTest {
    @Test fun `recovery cannot be overwritten by an in flight restoration`() = runTest {
        val gate = AuthSessionGate()
        val release = CompletableDeferred<Unit>()
        val sequence = mutableListOf<String>()
        var session = "absent"
        val restoration = launch {
            gate.exclusive {
                sequence += "restoration-start"
                release.await()
                session = "restored"
            }
        }
        runCurrent()
        val recovery = launch { gate.exclusive { sequence += "recovery"; session = "recovery" } }
        runCurrent()
        assertEquals(listOf("restoration-start"), sequence)
        restoration.cancelAndJoin()
        recovery.join()
        assertEquals("recovery", session)
    }

    @Test fun `gate serializes successful restoration then recovery`() = runTest {
        val gate = AuthSessionGate()
        val release = CompletableDeferred<Unit>()
        val sequence = mutableListOf<String>()
        val first = launch { gate.exclusive { release.await(); sequence += "restored" } }
        runCurrent()
        val second = launch { gate.exclusive { sequence += "recovery" } }
        runCurrent()
        release.complete(Unit)
        first.join(); second.join()
        assertEquals(listOf("restored", "recovery"), sequence)
    }
}
