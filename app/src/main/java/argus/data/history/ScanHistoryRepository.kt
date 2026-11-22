package argus.data.history

import argus.domain.model.ExportBundle
import argus.domain.model.ImportResult
import argus.domain.model.ScanSessionRecord
import argus.domain.model.SessionSource
import java.io.File

/**
 * Local file-backed repository for Argus scan history sessions, with JSON/CSV export and JSON import.
 */
class ScanHistoryRepository(
    private val storageFile: File,
    private val appVersion: String = "1.0.0",
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val lock = Any()

    fun loadAll(): List<ScanSessionRecord> = synchronized(lock) {
        if (!storageFile.exists()) return emptyList()
        return runCatching {
            val raw = storageFile.readText(Charsets.UTF_8)
            if (raw.isBlank()) return emptyList()
            ScanHistoryJsonCodec.decodeBundle(raw).sessions.sortedByDescending { it.startedAtMillis }
        }.getOrDefault(emptyList())
    }

    fun saveSession(session: ScanSessionRecord): List<ScanSessionRecord> = synchronized(lock) {
        val current = loadAll().toMutableList()
        current.removeAll { it.id == session.id }
        current.add(0, session)
        val sorted = current.sortedByDescending { it.startedAtMillis }
        persist(sorted)
        sorted
    }

    fun renameSession(sessionId: String, newTitle: String): List<ScanSessionRecord> = synchronized(lock) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return loadAll()
        val updated = loadAll().map { s ->
            if (s.id == sessionId) s.copy(title = trimmed) else s
        }
        persist(updated)
        updated
    }

    fun deleteSession(sessionId: String): List<ScanSessionRecord> = synchronized(lock) {
        val updated = loadAll().filterNot { it.id == sessionId }
        persist(updated)
        updated
    }

    fun clearAll(): List<ScanSessionRecord> = synchronized(lock) {
        if (storageFile.exists()) {
            storageFile.delete()
        }
        emptyList()
    }

    fun exportToJson(sessions: List<ScanSessionRecord>): String {
        val bundle = ExportBundle(
            schemaVersion = ExportBundle.CURRENT_SCHEMA_VERSION,
            exportedAtMillis = clock(),
            appVersion = appVersion,
            sessions = sessions
        )
        return ScanHistoryJsonCodec.encodeBundle(bundle)
    }

    fun exportToCsv(sessions: List<ScanSessionRecord>): String = ScanHistoryJsonCodec.encodeSessionsCsv(sessions)

    fun importFromJson(jsonString: String): Pair<ImportResult, List<ScanSessionRecord>> = synchronized(lock) {
        val bundle = ScanHistoryJsonCodec.decodeBundle(jsonString)
        val existing = loadAll()
        val existingIds = existing.mapTo(HashSet()) { it.id }

        val now = clock()
        var importedCount = 0
        var skippedCount = 0
        val merged = existing.toMutableList()

        for (rawSession in bundle.sessions) {
            if (existingIds.contains(rawSession.id)) {
                skippedCount++
            } else {
                existingIds.add(rawSession.id)
                merged.add(
                    rawSession.copy(
                        source = SessionSource.IMPORTED, importedAtMillis = now
                    )
                )
                importedCount++
            }
        }

        val sorted = merged.sortedByDescending { it.startedAtMillis }
        if (importedCount > 0) {
            persist(sorted)
        }

        val result = ImportResult(
            importedCount = importedCount, skippedDuplicateCount = skippedCount, totalInFile = bundle.sessions.size
        )
        result to sorted
    }

    private fun persist(sessions: List<ScanSessionRecord>) {
        storageFile.parentFile?.mkdirs()
        val json = exportToJson(sessions)
        storageFile.writeText(json, Charsets.UTF_8)
    }
}
