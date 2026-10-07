package com.exam.assistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.domain.ChapterStage
import com.exam.assistant.domain.StageLabels
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.stageLabelDataStore by preferencesDataStore(name = "stage_labels")

/** The student's chapter-stage labels (§5.1): renames, hidden stages, one custom stage. */
class StageLabelStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val hiddenKey = stringSetPreferencesKey("hidden")
    private val customKey = stringPreferencesKey("custom_label")

    private fun renameKey(stage: ChapterStage) = stringPreferencesKey("rename_${stage.name}")

    suspend fun load(): StageLabels = withContext(dispatchers.io) {
        context.stageLabelDataStore.data.first().toLabels()
    }

    suspend fun save(labels: StageLabels) = withContext(dispatchers.io) {
        context.stageLabelDataStore.edit { prefs ->
            ChapterStage.entries.forEach { stage ->
                val name = labels.renamed[stage]?.trim()
                if (name.isNullOrEmpty()) prefs.remove(renameKey(stage)) else prefs[renameKey(stage)] = name
            }
            prefs[hiddenKey] = labels.hidden.map { it.name }.toSet()
            val custom = labels.customLabel?.trim()
            if (custom.isNullOrEmpty()) prefs.remove(customKey) else prefs[customKey] = custom
        }
        Unit
    }

    private fun Preferences.toLabels() = StageLabels(
        renamed = ChapterStage.entries.mapNotNull { stage -> this[renameKey(stage)]?.let { stage to it } }.toMap(),
        hidden = this[hiddenKey].orEmpty().mapNotNull { name -> ChapterStage.entries.firstOrNull { it.name == name } }.toSet(),
        customLabel = this[customKey],
    )
}
