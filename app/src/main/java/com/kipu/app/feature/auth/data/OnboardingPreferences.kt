package com.kipu.app.feature.auth.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.introPreferences by preferencesDataStore("kipu_introduction")

data class OnboardingCheckpoint(val completed: Boolean = false, val page: Int = 0)

/** Installation-only presentation checkpoint; never holds credentials or account data. */
class OnboardingPreferenceStore(private val dataStore: DataStore<Preferences>) {
    private val completedKey = booleanPreferencesKey("completed")
    private val pageKey = intPreferencesKey("page")
    val checkpoint = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { OnboardingCheckpoint(it[completedKey] ?: false, (it[pageKey] ?: 0).coerceIn(0, 2)) }

    suspend fun savePage(page: Int) {
        dataStore.edit { it[pageKey] = page.coerceIn(0, 2) }
    }

    suspend fun complete() {
        dataStore.edit { it[completedKey] = true }
    }
}

@Singleton
class OnboardingPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val store = OnboardingPreferenceStore(context.introPreferences)
    val checkpoint = store.checkpoint
    suspend fun savePage(page: Int) = store.savePage(page)
    suspend fun complete() = store.complete()
}
