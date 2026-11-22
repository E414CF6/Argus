package argus.data.history

import argus.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScanHistoryJsonCodecAndRepoTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun sampleSession(
        id: String = "sess-1", title: String = "Room \"302\" Midterm", startedAt: Long = 1_700_000_000_000L
    ): ScanSessionRecord = ScanSessionRecord(
        id = id,
        title = title,
        startedAtMillis = startedAt,
        endedAtMillis = startedAt + 120_000L,
        durationSec = 120L,
        baselineDurationSec = 60,
        baselineDeviceCount = 2,
        totalPacketsProcessed = 340L,
        blePacketsProcessed = 290L,
        wifiPacketsProcessed = 50L,
        baselineDevices = listOf(
            BaselineDeviceRecord(
                clusterKey = "fp_base_1",
                displayName = "HallwayWatch",
                manufacturer = "Samsung",
                type = DeviceType.BLE,
                maxRssi = -74,
                isSuspiciousAudioOrWearable = true
            )
        ),
        suspiciousAlerts = listOf(
            SuspiciousAlertRecord(
                clusterKey = "fp_alert_1",
                displayName = "AirPods Pro, \"Student\"",
                manufacturer = "Apple",
                type = DeviceType.BLE,
                reason = AlertReason.NEW_DEVICE,
                riskScore = 88,
                rawRssi = -51,
                filteredRssi = -52.4,
                baselineMaxRssi = null,
                observationCountIn15s = 5,
                isSuspiciousAudioOrWearable = true,
                firstDetectedAt = startedAt + 70_000L,
                lastDetectedAt = startedAt + 95_000L
            )
        ),
        powerOffEvents = listOf(
            PowerOffEvent(
                clusterKey = "fp_alert_1",
                displayName = "AirPods Pro, \"Student\"",
                manufacturer = "Apple",
                type = DeviceType.BLE,
                lastRssi = -51,
                disappearedAtMillis = startedAt + 115_000L
            )
        ),
        source = SessionSource.LOCAL
    )

    @Test
    fun `JSON ExportBundle round-trip preserves all fields including escaped strings`() {
        val original = sampleSession()
        val bundle = ExportBundle(
            exportedAtMillis = 1_700_000_500_000L, appVersion = "1.0.0", sessions = listOf(original)
        )

        val json = ScanHistoryJsonCodec.encodeBundle(bundle)
        val decoded = ScanHistoryJsonCodec.decodeBundle(json)

        assertThat(decoded.schemaVersion).isEqualTo(1)
        assertThat(decoded.sessions).hasSize(1)
        val restored = decoded.sessions.first()
        assertThat(restored.id).isEqualTo(original.id)
        assertThat(restored.title).isEqualTo(original.title)
        assertThat(restored.totalPacketsProcessed).isEqualTo(340L)
        assertThat(restored.blePacketsProcessed).isEqualTo(290L)
        assertThat(restored.wifiPacketsProcessed).isEqualTo(50L)
        assertThat(restored.baselineDevices).hasSize(1)
        assertThat(restored.suspiciousAlerts).hasSize(1)
        assertThat(restored.suspiciousAlerts.first().riskScore).isEqualTo(88)
        assertThat(restored.suspiciousAlerts.first().displayName).isEqualTo("AirPods Pro, \"Student\"")
        assertThat(restored.powerOffEvents).hasSize(1)
    }

    @Test
    fun `CSV export escapes commas and quotes properly and includes alert and poweroff rows`() {
        val session = sampleSession()
        val csv = ScanHistoryJsonCodec.encodeSessionsCsv(listOf(session))

        val lines = csv.trim().lines()
        assertThat(lines).hasSize(3) // header + 1 alert + 1 poweroff
        assertThat(lines[1]).contains("ALERT")
        assertThat(lines[1]).contains("\"AirPods Pro, \"\"Student\"\"\"")
        assertThat(lines[2]).contains("POWER_OFF")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decodeBundle throws on invalid JSON`() {
        ScanHistoryJsonCodec.decodeBundle("{ invalid json")
    }

    @Test
    fun `Repository persists, renames, deletes, exports, and deduplicates imported sessions`() {
        var now = 1_700_001_000_000L
        val file = tempFolder.newFile("history.json")
        val repo = ScanHistoryRepository(storageFile = file, clock = { now })

        val s1 = sampleSession(id = "s1", title = "Room 101", startedAt = 1_700_000_100_000L)
        repo.saveSession(s1)
        assertThat(repo.loadAll()).hasSize(1)

        repo.renameSession("s1", "Room 101 Final")
        assertThat(repo.loadAll().first().title).isEqualTo("Room 101 Final")

        // Export s1 and another session s2 from another device
        val s2 = sampleSession(id = "s2", title = "Room 202", startedAt = 1_700_000_200_000L)
        val importJson = repo.exportToJson(listOf(s1, s2))

        val (importResult, merged) = repo.importFromJson(importJson)
        assertThat(importResult.totalInFile).isEqualTo(2)
        assertThat(importResult.importedCount).isEqualTo(1)
        assertThat(importResult.skippedDuplicateCount).isEqualTo(1)
        assertThat(merged).hasSize(2)

        val importedS2 = merged.first { it.id == "s2" }
        assertThat(importedS2.source).isEqualTo(SessionSource.IMPORTED)
        assertThat(importedS2.importedAtMillis).isEqualTo(now)

        val afterDelete = repo.deleteSession("s1")
        assertThat(afterDelete).hasSize(1)
        assertThat(afterDelete.first().id).isEqualTo("s2")

        val afterClear = repo.clearAll()
        assertThat(afterClear).isEmpty()
        assertThat(repo.loadAll()).isEmpty()
    }
}
