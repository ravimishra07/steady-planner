package com.exam.assistant.core.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.customSyllabusDataStore
import com.exam.assistant.core.data.dataStore
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.planDataStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.planPromptDataStore
import com.exam.assistant.core.data.stageLabelDataStore
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** How an import ended. Anything but [Imported] leaves the current data untouched. */
sealed interface ImportResult {
    data object Imported : ImportResult

    /** Made by a newer app version (a higher dbVersion or schemaVersion) than this one. */
    data object TooNew : ImportResult

    /** An older backup whose tables differ from this version's. */
    data object Incompatible : ImportResult

    /** Not a backup file, damaged, or unreadable. */
    data object Unreadable : ImportResult
}

/**
 * Export and import of everything the student owns (PRODUCTION_V1_PLAN §10.4). Free, always.
 *
 * Format — one JSON file:
 * ```
 * { "schemaVersion": 1, "dbVersion": 11, "exportedAt": "<ISO-8601>",
 *   "tables": { "<table>": { "columns": [..], "rows": [[..], ..] } },
 *   "prefs":  { "<datastore>": { "<key>": { "type": "string", "value": .. } } } }
 * ```
 * Tables are copied generically — every Room table, column by column — so a new
 * table or column is covered without touching this file. BLOB cells are
 * `{ "base64": ".." }`.
 *
 * Version rules:
 * - `schemaVersion` is this envelope's format. Only 1 exists.
 * - `dbVersion` higher than this build's Room version: rejected ([ImportResult.TooNew]).
 * - `dbVersion` equal: accepted.
 * - `dbVersion` lower: accepted only when every current table exists in the file
 *   with exactly the same columns — i.e. the Room migrations in between did not
 *   change the schema. Otherwise rejected ([ImportResult.Incompatible]); replaying
 *   Room migrations over a backup is not supported.
 *
 * Import replaces, never merges: every table is emptied and refilled in one
 * transaction (foreign keys deferred), then the user-data DataStores are
 * replaced. Not exported: the live Focus timer and migration bookkeeping,
 * which describe this device rather than the student's data; the fast
 * "has attempt" flag is recomputed from the imported rows instead.
 */
class BackupRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers,
    private val attemptRepository: AttemptRepository,
) {
    private val room by lazy { PrepTrackerDatabase.get(context) }

    /** The DataStores holding the student's own data, by file name. */
    private val userDataStores: Map<String, DataStore<Preferences>>
        get() = linkedMapOf(
            "settings" to context.dataStore,
            "custom_syllabus" to context.customSyllabusDataStore,
            "stage_labels" to context.stageLabelDataStore,
            "plan" to context.planDataStore,
            "plan_prompts" to context.planPromptDataStore,
        )

    suspend fun exportTo(uri: Uri): Boolean = withContext(dispatchers.io) {
        runCatching {
            val json = buildBackup().toString()
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("No output stream")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        }.isSuccess
    }

    suspend fun importFrom(uri: Uri): ImportResult = withContext(dispatchers.io) {
        val root = runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("No input stream")
            JSONObject(text)
        }.getOrNull() ?: return@withContext ImportResult.Unreadable
        val schemaVersion = root.optInt(KEY_SCHEMA_VERSION, -1)
        val dbVersion = root.optInt(KEY_DB_VERSION, -1)
        val tables = root.optJSONObject(KEY_TABLES)
        if (schemaVersion < 1 || dbVersion < 1 || tables == null) return@withContext ImportResult.Unreadable
        if (schemaVersion > SCHEMA_VERSION) return@withContext ImportResult.TooNew
        val sql = room.openHelper.writableDatabase
        val current = sql.version
        if (dbVersion > current) return@withContext ImportResult.TooNew
        val currentTables = userTables(sql).associateWith { columnsOf(sql, it) }
        if (dbVersion < current) {
            val sameSchema = currentTables.all { (table, columns) ->
                val saved = tables.optJSONObject(table)?.optJSONArray(KEY_COLUMNS) ?: return@all false
                saved.strings().toSet() == columns.toSet()
            }
            if (!sameSchema) return@withContext ImportResult.Incompatible
        }
        val restored = runCatching {
            room.withTransaction {
                sql.execSQL("PRAGMA defer_foreign_keys = ON")
                currentTables.keys.forEach { sql.execSQL("DELETE FROM `$it`") }
                currentTables.forEach { (table, columns) ->
                    tables.optJSONObject(table)?.let { insertRows(sql, table, columns.toSet(), it) }
                }
            }
        }
        if (restored.isFailure) return@withContext ImportResult.Unreadable
        val prefs = root.optJSONObject(KEY_PREFS) ?: JSONObject()
        userDataStores.forEach { (name, store) -> restorePrefs(store, prefs.optJSONObject(name)) }
        runCatching { attemptRepository.refreshHasAttemptFlag() }
        ImportResult.Imported
    }

    private suspend fun buildBackup(): JSONObject {
        val sql = room.openHelper.readableDatabase
        val tables = JSONObject()
        // One read transaction: the file is a consistent snapshot even if a write lands mid-export.
        room.withTransaction {
            userTables(sql).forEach { table -> tables.put(table, dumpTable(sql, table)) }
        }
        val prefs = JSONObject()
        userDataStores.forEach { (name, store) -> prefs.put(name, dumpPrefs(store.data.first())) }
        return JSONObject()
            .put(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .put(KEY_DB_VERSION, sql.version)
            .put(KEY_EXPORTED_AT, Instant.now().toString())
            .put(KEY_TABLES, tables)
            .put(KEY_PREFS, prefs)
    }

    private fun userTables(sql: SupportSQLiteDatabase): List<String> =
        sql.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table', 'android_metadata') ORDER BY name",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun columnsOf(sql: SupportSQLiteDatabase, table: String): List<String> =
        sql.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildList { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
        }

    private fun dumpTable(sql: SupportSQLiteDatabase, table: String): JSONObject {
        val rows = JSONArray()
        val columns = JSONArray()
        sql.query("SELECT * FROM `$table`").use { cursor ->
            cursor.columnNames.forEach { columns.put(it) }
            while (cursor.moveToNext()) {
                val row = JSONArray()
                for (i in 0 until cursor.columnCount) row.put(cellOf(cursor, i))
                rows.put(row)
            }
        }
        return JSONObject().put(KEY_COLUMNS, columns).put(KEY_ROWS, rows)
    }

    private fun cellOf(cursor: Cursor, index: Int): Any = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_BLOB -> JSONObject().put(KEY_BASE64, Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
        else -> cursor.getString(index)
    }

    /** Only columns that exist in this version's table are written; the rest take their defaults. */
    private fun insertRows(sql: SupportSQLiteDatabase, table: String, known: Set<String>, saved: JSONObject) {
        val columns = saved.optJSONArray(KEY_COLUMNS)?.strings() ?: return
        val rows = saved.optJSONArray(KEY_ROWS) ?: return
        for (r in 0 until rows.length()) {
            val row = rows.getJSONArray(r)
            val values = ContentValues()
            columns.forEachIndexed { i, column ->
                if (column in known && i < row.length()) values.putCell(column, row.get(i))
            }
            sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
        }
    }

    private fun ContentValues.putCell(column: String, value: Any?) {
        when (value) {
            null, JSONObject.NULL -> putNull(column)
            is Int -> put(column, value.toLong())
            is Long -> put(column, value)
            is Double -> put(column, value)
            is Float -> put(column, value.toDouble())
            is Boolean -> put(column, if (value) 1L else 0L)
            is JSONObject -> put(column, Base64.decode(value.getString(KEY_BASE64), Base64.NO_WRAP))
            is Number -> put(column, value.toDouble())
            else -> put(column, value.toString())
        }
    }

    private fun dumpPrefs(preferences: Preferences): JSONObject {
        val out = JSONObject()
        preferences.asMap().forEach { (key, value) ->
            val (type, encoded) = when (value) {
                is Boolean -> TYPE_BOOLEAN to value
                is Int -> TYPE_INT to value
                is Long -> TYPE_LONG to value
                is Float -> TYPE_FLOAT to value.toDouble()
                is Double -> TYPE_DOUBLE to value
                is String -> TYPE_STRING to value
                is Set<*> -> TYPE_STRING_SET to JSONArray(value.map { it.toString() })
                else -> return@forEach
            }
            out.put(key.name, JSONObject().put(KEY_TYPE, type).put(KEY_VALUE, encoded))
        }
        return out
    }

    /** Replaces the whole store; a store missing from the file ends up empty, as on a fresh install. */
    private suspend fun restorePrefs(store: DataStore<Preferences>, saved: JSONObject?) {
        store.edit { prefs ->
            prefs.clear()
            saved ?: return@edit
            saved.keys().forEach { name -> runCatching { prefs.putTyped(name, saved.getJSONObject(name)) } }
        }
    }

    private fun MutablePreferences.putTyped(name: String, entry: JSONObject) {
        when (entry.getString(KEY_TYPE)) {
            TYPE_BOOLEAN -> this[booleanPreferencesKey(name)] = entry.getBoolean(KEY_VALUE)
            TYPE_INT -> this[intPreferencesKey(name)] = entry.getInt(KEY_VALUE)
            TYPE_LONG -> this[longPreferencesKey(name)] = entry.getLong(KEY_VALUE)
            TYPE_FLOAT -> this[floatPreferencesKey(name)] = entry.getDouble(KEY_VALUE).toFloat()
            TYPE_DOUBLE -> this[doublePreferencesKey(name)] = entry.getDouble(KEY_VALUE)
            TYPE_STRING -> this[stringPreferencesKey(name)] = entry.getString(KEY_VALUE)
            TYPE_STRING_SET -> this[stringSetPreferencesKey(name)] = entry.getJSONArray(KEY_VALUE).strings().toSet()
        }
    }

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    companion object {
        const val SCHEMA_VERSION = 1

        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_DB_VERSION = "dbVersion"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_TABLES = "tables"
        private const val KEY_PREFS = "prefs"
        private const val KEY_COLUMNS = "columns"
        private const val KEY_ROWS = "rows"
        private const val KEY_BASE64 = "base64"
        private const val KEY_TYPE = "type"
        private const val KEY_VALUE = "value"
        private const val TYPE_BOOLEAN = "boolean"
        private const val TYPE_INT = "int"
        private const val TYPE_LONG = "long"
        private const val TYPE_FLOAT = "float"
        private const val TYPE_DOUBLE = "double"
        private const val TYPE_STRING = "string"
        private const val TYPE_STRING_SET = "stringSet"
    }
}
