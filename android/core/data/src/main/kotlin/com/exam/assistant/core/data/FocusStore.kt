package com.exam.assistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.domain.BlockTag
import com.exam.assistant.domain.FocusBlockRef
import com.exam.assistant.domain.FocusSession
import com.exam.assistant.domain.FocusStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.focusDataStore by preferencesDataStore(name = "focus")

class FocusStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val statusKey = stringPreferencesKey("status")
    private val durationKey = intPreferencesKey("duration_sec")
    private val remainingKey = intPreferencesKey("remaining_sec")
    private val endsAtKey = longPreferencesKey("ends_at")
    private val blockIdKey = stringPreferencesKey("block_id")
    private val blockTitleKey = stringPreferencesKey("block_title")
    private val blockSubtitleKey = stringPreferencesKey("block_subtitle")
    private val blockTagKey = stringPreferencesKey("block_tag")
    private val blockSessionIdKey = stringPreferencesKey("block_session_id")
    private val blockNodeKeyKey = stringPreferencesKey("block_node_key")
    private val blockIsRevisionKey = booleanPreferencesKey("block_is_revision")
    private val completedTodayKey = intPreferencesKey("completed_today")

    suspend fun load(): FocusSession = withContext(dispatchers.io) {
        val prefs = context.focusDataStore.data.first()
        val status = FocusStatus.valueOf(prefs[statusKey] ?: FocusStatus.IDLE.name)
        val session = FocusSession(
            status = status,
            durationSec = prefs[durationKey] ?: com.exam.assistant.domain.DEFAULT_FOCUS_DURATION_SEC,
            remainingSec = prefs[remainingKey] ?: (prefs[durationKey] ?: com.exam.assistant.domain.DEFAULT_FOCUS_DURATION_SEC),
            endsAtMs = prefs[endsAtKey],
            block = prefs[blockIdKey]?.let { id ->
                FocusBlockRef(
                    id = id,
                    title = prefs[blockTitleKey].orEmpty(),
                    subtitle = prefs[blockSubtitleKey].orEmpty(),
                    tag = prefs[blockTagKey]?.let { runCatching { BlockTag.valueOf(it) }.getOrNull() },
                    sessionId = prefs[blockSessionIdKey].orEmpty(),
                    nodeKey = prefs[blockNodeKeyKey].orEmpty(),
                    isRevision = prefs[blockIsRevisionKey] ?: false,
                )
            },
            completedToday = prefs[completedTodayKey] ?: 0,
        )
        session.withClockNow()
    }

    suspend fun save(session: FocusSession) = withContext(dispatchers.io) {
        context.focusDataStore.edit { prefs ->
            prefs[statusKey] = session.status.name
            prefs[durationKey] = session.durationSec
            prefs[remainingKey] = session.remainingSec
            val endsAt = session.endsAtMs
            if (endsAt != null) prefs[endsAtKey] = endsAt else prefs.remove(endsAtKey)
            val block = session.block
            if (block != null) {
                prefs[blockIdKey] = block.id
                prefs[blockTitleKey] = block.title
                prefs[blockSubtitleKey] = block.subtitle
                val tagName = block.tag?.name
                if (tagName != null) prefs[blockTagKey] = tagName else prefs.remove(blockTagKey)
                prefs[blockSessionIdKey] = block.sessionId
                prefs[blockNodeKeyKey] = block.nodeKey
                prefs[blockIsRevisionKey] = block.isRevision
            } else {
                prefs.remove(blockIdKey)
                prefs.remove(blockTitleKey)
                prefs.remove(blockSubtitleKey)
                prefs.remove(blockTagKey)
                prefs.remove(blockSessionIdKey)
                prefs.remove(blockNodeKeyKey)
                prefs.remove(blockIsRevisionKey)
            }
            prefs[completedTodayKey] = session.completedToday
        }
        Unit
    }

    suspend fun clear() = withContext(dispatchers.io) {
        context.focusDataStore.edit { it.clear() }
        Unit
    }
}
