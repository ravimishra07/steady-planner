package com.exam.assistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.exam.assistant.core.common.AppDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.syllabusDataStore by preferencesDataStore(name = "syllabus_ui")

class SyllabusStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val doneKey = stringSetPreferencesKey("done_leaves")
    private val openKey = stringSetPreferencesKey("open_nodes")
    private val sectionKey = intPreferencesKey("section_index")
    private val excludedSectionKey = stringSetPreferencesKey("excluded_sections")

    suspend fun load(): SyllabusUiState = withContext(dispatchers.io) {
        val prefs = context.syllabusDataStore.data.first()
        SyllabusUiState(
            doneLeaves = prefs[doneKey].orEmpty(),
            openNodes = prefs[openKey].orEmpty(),
            sectionIndex = prefs[sectionKey] ?: 0,
            excludedSectionKeys = prefs[excludedSectionKey].orEmpty(),
        )
    }

    suspend fun save(state: SyllabusUiState) = withContext(dispatchers.io) {
        context.syllabusDataStore.edit {
            it[doneKey] = state.doneLeaves
            it[openKey] = state.openNodes
            it[sectionKey] = state.sectionIndex
            it[excludedSectionKey] = state.excludedSectionKeys
        }
        Unit
    }

    suspend fun clear() = withContext(dispatchers.io) {
        context.syllabusDataStore.edit { it.clear() }
        Unit
    }
}
