package argus.data.history

import argus.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pure-Kotlin JSON & CSV codec for Argus scan history export/import and local persistence.
 * Zero Android/external dependencies so it runs deterministically in JVM unit tests as well.
 */
object ScanHistoryJsonCodec {

    fun encodeBundle(bundle: ExportBundle): String {
        val sb = StringBuilder(2048)
        sb.append("{\n")
        sb.append("  \"schemaVersion\": ").append(bundle.schemaVersion).append(",\n")
        sb.append("  \"exportedAtMillis\": ").append(bundle.exportedAtMillis).append(",\n")
        sb.append("  \"appVersion\": ").append(jsonStr(bundle.appVersion)).append(",\n")
        sb.append("  \"sessions\": ")
        appendSessionsArray(sb, bundle.sessions, indent = "  ")
        sb.append("\n}")
        return sb.toString()
    }

    fun decodeBundle(json: String): ExportBundle {
        return when (val root = MiniJsonParser(json).parseValue()) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST") val obj = root as Map<String, Any?>
                // Support both ExportBundle format and a single session object if imported directly
                if (obj.containsKey("sessions")) {
                    val schemaVersion = obj.int("schemaVersion", ExportBundle.CURRENT_SCHEMA_VERSION)
                    val exportedAt = obj.long("exportedAtMillis", System.currentTimeMillis())
                    val appVersion = obj.str("appVersion", "1.0.0")
                    val rawSessions =
                        obj["sessions"] as? List<*> ?: throw IllegalArgumentException("Missing 'sessions' array")

                    @Suppress("UNCHECKED_CAST") val sessions = rawSessions.map {
                        val map = it as? Map<String, Any?> ?: throw IllegalArgumentException("Invalid session entry")
                        parseSession(map)
                    }
                    ExportBundle(
                        schemaVersion = schemaVersion,
                        exportedAtMillis = exportedAt,
                        appVersion = appVersion,
                        sessions = sessions
                    )
                } else if (obj.containsKey("id") && obj.containsKey("startedAtMillis")) {
                    ExportBundle(
                        schemaVersion = ExportBundle.CURRENT_SCHEMA_VERSION,
                        exportedAtMillis = System.currentTimeMillis(),
                        appVersion = "1.0.0",
                        sessions = listOf(parseSession(obj))
                    )
                } else {
                    throw IllegalArgumentException("Unrecognized Argus JSON structure")
                }
            }

            is List<*> -> {
                val sessions = root.map {
                    @Suppress("UNCHECKED_CAST") parseSession(it as Map<String, Any?>)
                }
                ExportBundle(
                    schemaVersion = ExportBundle.CURRENT_SCHEMA_VERSION,
                    exportedAtMillis = System.currentTimeMillis(),
                    appVersion = "1.0.0",
                    sessions = sessions
                )
            }

            else -> throw IllegalArgumentException("Root JSON must be an object or array")
        }
    }

    fun encodeSessionsCsv(sessions: List<ScanSessionRecord>): String {
        val sb = StringBuilder(1024)
        val dtFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        sb.appendLine("session_id,session_title,session_start,duration_sec,baseline_devices,total_packets,ble_packets,wifi_packets,record_type,device_name,manufacturer,device_type,cluster_key,alert_reason,risk_score,rssi_dbm,baseline_max_rssi,obs_15s,audio_or_wearable,event_time")

        for (session in sessions) {
            val startStr = dtFmt.format(Date(session.startedAtMillis))
            if (session.suspiciousAlerts.isEmpty() && session.powerOffEvents.isEmpty()) {
                sb.appendLine(
                    listOf(
                        session.id,
                        session.title,
                        startStr,
                        session.durationSec.toString(),
                        session.baselineDeviceCount.toString(),
                        session.totalPacketsProcessed.toString(),
                        session.blePacketsProcessed.toString(),
                        session.wifiPacketsProcessed.toString(),
                        "SESSION_SUMMARY",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        startStr
                    ).joinToString(",") { csvEscape(it) })
            } else {
                for (alert in session.suspiciousAlerts) {
                    sb.appendLine(
                        listOf(
                            session.id,
                            session.title,
                            startStr,
                            session.durationSec.toString(),
                            session.baselineDeviceCount.toString(),
                            session.totalPacketsProcessed.toString(),
                            session.blePacketsProcessed.toString(),
                            session.wifiPacketsProcessed.toString(),
                            "ALERT",
                            alert.displayName,
                            alert.manufacturer,
                            alert.type.name,
                            alert.clusterKey,
                            alert.reason.name,
                            alert.riskScore.toString(),
                            alert.rawRssi.toString(),
                            alert.baselineMaxRssi?.toString().orEmpty(),
                            alert.observationCountIn15s.toString(),
                            alert.isSuspiciousAudioOrWearable.toString(),
                            dtFmt.format(Date(alert.lastDetectedAt))
                        ).joinToString(",") { csvEscape(it) })
                }
                for (ev in session.powerOffEvents) {
                    sb.appendLine(
                        listOf(
                            session.id,
                            session.title,
                            startStr,
                            session.durationSec.toString(),
                            session.baselineDeviceCount.toString(),
                            session.totalPacketsProcessed.toString(),
                            session.blePacketsProcessed.toString(),
                            session.wifiPacketsProcessed.toString(),
                            "POWER_OFF",
                            ev.displayName,
                            ev.manufacturer,
                            ev.type.name,
                            ev.clusterKey,
                            "POWER_OFF",
                            "",
                            ev.lastRssi.toString(),
                            "",
                            "",
                            "",
                            dtFmt.format(Date(ev.disappearedAtMillis))
                        ).joinToString(",") { csvEscape(it) })
                }
            }
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun appendSessionsArray(sb: StringBuilder, sessions: List<ScanSessionRecord>, indent: String) {
        if (sessions.isEmpty()) {
            sb.append("[]")
            return
        }
        sb.append("[\n")
        sessions.forEachIndexed { idx, session ->
            val inner = "$indent  "
            sb.append(inner).append("{\n")
            val f = "$inner  "
            sb.append(f).append("\"id\": ").append(jsonStr(session.id)).append(",\n")
            sb.append(f).append("\"title\": ").append(jsonStr(session.title)).append(",\n")
            sb.append(f).append("\"startedAtMillis\": ").append(session.startedAtMillis).append(",\n")
            sb.append(f).append("\"endedAtMillis\": ").append(session.endedAtMillis).append(",\n")
            sb.append(f).append("\"durationSec\": ").append(session.durationSec).append(",\n")
            sb.append(f).append("\"baselineDurationSec\": ").append(session.baselineDurationSec).append(",\n")
            sb.append(f).append("\"baselineDeviceCount\": ").append(session.baselineDeviceCount).append(",\n")
            sb.append(f).append("\"totalPacketsProcessed\": ").append(session.totalPacketsProcessed).append(",\n")
            sb.append(f).append("\"blePacketsProcessed\": ").append(session.blePacketsProcessed).append(",\n")
            sb.append(f).append("\"wifiPacketsProcessed\": ").append(session.wifiPacketsProcessed).append(",\n")
            sb.append(f).append("\"source\": ").append(jsonStr(session.source.name)).append(",\n")
            sb.append(f).append("\"importedAtMillis\": ").append(session.importedAtMillis?.toString() ?: "null")
                .append(",\n")
            sb.append(f).append("\"baselineDevices\": ")
            appendBaselineArray(sb, session.baselineDevices, f)
            sb.append(",\n")
            sb.append(f).append("\"suspiciousAlerts\": ")
            appendAlertsArray(sb, session.suspiciousAlerts, f)
            sb.append(",\n")
            sb.append(f).append("\"powerOffEvents\": ")
            appendPowerOffArray(sb, session.powerOffEvents, f)
            sb.append("\n")
            sb.append(inner).append("}")
            if (idx < sessions.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append(indent).append("]")
    }

    private fun appendBaselineArray(sb: StringBuilder, list: List<BaselineDeviceRecord>, indent: String) {
        if (list.isEmpty()) {
            sb.append("[]")
            return
        }
        sb.append("[\n")
        list.forEachIndexed { idx, d ->
            sb.append(indent).append("  {")
            sb.append("\"clusterKey\": ").append(jsonStr(d.clusterKey)).append(", ")
            sb.append("\"displayName\": ").append(jsonStr(d.displayName)).append(", ")
            sb.append("\"manufacturer\": ").append(jsonStr(d.manufacturer)).append(", ")
            sb.append("\"type\": ").append(jsonStr(d.type.name)).append(", ")
            sb.append("\"maxRssi\": ").append(d.maxRssi).append(", ")
            sb.append("\"isSuspiciousAudioOrWearable\": ").append(d.isSuspiciousAudioOrWearable)
            sb.append("}")
            if (idx < list.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append(indent).append("]")
    }

    private fun appendAlertsArray(sb: StringBuilder, list: List<SuspiciousAlertRecord>, indent: String) {
        if (list.isEmpty()) {
            sb.append("[]")
            return
        }
        sb.append("[\n")
        list.forEachIndexed { idx, a ->
            sb.append(indent).append("  {")
            sb.append("\"clusterKey\": ").append(jsonStr(a.clusterKey)).append(", ")
            sb.append("\"displayName\": ").append(jsonStr(a.displayName)).append(", ")
            sb.append("\"manufacturer\": ").append(jsonStr(a.manufacturer)).append(", ")
            sb.append("\"type\": ").append(jsonStr(a.type.name)).append(", ")
            sb.append("\"reason\": ").append(jsonStr(a.reason.name)).append(", ")
            sb.append("\"riskScore\": ").append(a.riskScore).append(", ")
            sb.append("\"rawRssi\": ").append(a.rawRssi).append(", ")
            sb.append("\"filteredRssi\": ").append(a.filteredRssi).append(", ")
            sb.append("\"baselineMaxRssi\": ").append(a.baselineMaxRssi?.toString() ?: "null").append(", ")
            sb.append("\"observationCountIn15s\": ").append(a.observationCountIn15s).append(", ")
            sb.append("\"isSuspiciousAudioOrWearable\": ").append(a.isSuspiciousAudioOrWearable).append(", ")
            sb.append("\"firstDetectedAt\": ").append(a.firstDetectedAt).append(", ")
            sb.append("\"lastDetectedAt\": ").append(a.lastDetectedAt)
            sb.append("}")
            if (idx < list.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append(indent).append("]")
    }

    private fun appendPowerOffArray(sb: StringBuilder, list: List<PowerOffEvent>, indent: String) {
        if (list.isEmpty()) {
            sb.append("[]")
            return
        }
        sb.append("[\n")
        list.forEachIndexed { idx, ev ->
            sb.append(indent).append("  {")
            sb.append("\"clusterKey\": ").append(jsonStr(ev.clusterKey)).append(", ")
            sb.append("\"displayName\": ").append(jsonStr(ev.displayName)).append(", ")
            sb.append("\"manufacturer\": ").append(jsonStr(ev.manufacturer)).append(", ")
            sb.append("\"type\": ").append(jsonStr(ev.type.name)).append(", ")
            sb.append("\"lastRssi\": ").append(ev.lastRssi).append(", ")
            sb.append("\"disappearedAtMillis\": ").append(ev.disappearedAtMillis)
            sb.append("}")
            if (idx < list.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append(indent).append("]")
    }

    private fun jsonStr(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u%04x".format(ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSession(obj: Map<String, Any?>): ScanSessionRecord {
        val id = obj.reqStr("id")
        val title = obj.str("title", "Scan Session")
        val startedAt = obj.long("startedAtMillis", 0L)
        val endedAt = obj.long("endedAtMillis", startedAt)
        val durationSec = obj.long("durationSec", ((endedAt - startedAt) / 1000L).coerceAtLeast(1L))
        val baselineDurationSec = obj.int("baselineDurationSec", 60)
        val baselineDeviceCount = obj.int("baselineDeviceCount", 0)
        val totalPackets = obj.long("totalPacketsProcessed", 0L)
        val blePackets = obj.long("blePacketsProcessed", 0L)
        val wifiPackets = obj.long("wifiPacketsProcessed", 0L)
        val source = runCatching { SessionSource.valueOf(obj.str("source", "LOCAL")) }.getOrDefault(SessionSource.LOCAL)
        val importedAt = obj.optLong("importedAtMillis")

        val baselineList = (obj["baselineDevices"] as? List<*>)?.mapNotNull { item ->
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            BaselineDeviceRecord(
                clusterKey = m.reqStr("clusterKey"),
                displayName = m.str("displayName", "UNKNOWN"),
                manufacturer = m.str("manufacturer", "Generic"),
                type = enumOrDefault(m.str("type", "BLE"), DeviceType.BLE),
                maxRssi = m.int("maxRssi", -75),
                isSuspiciousAudioOrWearable = m.bool("isSuspiciousAudioOrWearable", false)
            )
        }.orEmpty()

        val alertsList = (obj["suspiciousAlerts"] as? List<*>)?.mapNotNull { item ->
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            SuspiciousAlertRecord(
                clusterKey = m.reqStr("clusterKey"),
                displayName = m.str("displayName", "UNKNOWN"),
                manufacturer = m.str("manufacturer", "Generic"),
                type = enumOrDefault(m.str("type", "BLE"), DeviceType.BLE),
                reason = enumOrDefault(m.str("reason", "NEW_DEVICE"), AlertReason.NEW_DEVICE),
                riskScore = m.int("riskScore", 0),
                rawRssi = m.int("rawRssi", -70),
                filteredRssi = m.double("filteredRssi", -70.0),
                baselineMaxRssi = m.optInt("baselineMaxRssi"),
                observationCountIn15s = m.int("observationCountIn15s", 1),
                isSuspiciousAudioOrWearable = m.bool("isSuspiciousAudioOrWearable", false),
                firstDetectedAt = m.long("firstDetectedAt", startedAt),
                lastDetectedAt = m.long("lastDetectedAt", endedAt)
            )
        }.orEmpty()

        val powerOffList = (obj["powerOffEvents"] as? List<*>)?.mapNotNull { item ->
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            PowerOffEvent(
                clusterKey = m.reqStr("clusterKey"),
                displayName = m.str("displayName", "UNKNOWN"),
                manufacturer = m.str("manufacturer", "Generic"),
                type = enumOrDefault(m.str("type", "BLE"), DeviceType.BLE),
                lastRssi = m.int("lastRssi", -70),
                disappearedAtMillis = m.long("disappearedAtMillis", endedAt)
            )
        }.orEmpty()

        return ScanSessionRecord(
            id = id,
            title = title,
            startedAtMillis = startedAt,
            endedAtMillis = endedAt,
            durationSec = durationSec,
            baselineDurationSec = baselineDurationSec,
            baselineDeviceCount = maxOf(baselineDeviceCount, baselineList.size),
            totalPacketsProcessed = totalPackets,
            blePacketsProcessed = blePackets,
            wifiPacketsProcessed = wifiPackets,
            baselineDevices = baselineList,
            suspiciousAlerts = alertsList,
            powerOffEvents = powerOffList,
            source = source,
            importedAtMillis = importedAt
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String, fallback: T): T =
        runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)

    private fun Map<String, Any?>.reqStr(key: String): String =
        (this[key] as? String) ?: throw IllegalArgumentException("Missing string field '$key'")

    private fun Map<String, Any?>.str(key: String, default: String): String = (this[key] as? String) ?: default

    private fun Map<String, Any?>.int(key: String, default: Int): Int = (this[key] as? Number)?.toInt() ?: default

    private fun Map<String, Any?>.optInt(key: String): Int? = (this[key] as? Number)?.toInt()

    private fun Map<String, Any?>.long(key: String, default: Long): Long = (this[key] as? Number)?.toLong() ?: default

    private fun Map<String, Any?>.optLong(key: String): Long? = (this[key] as? Number)?.toLong()

    private fun Map<String, Any?>.double(key: String, default: Double): Double =
        (this[key] as? Number)?.toDouble() ?: default

    private fun Map<String, Any?>.bool(key: String, default: Boolean): Boolean = (this[key] as? Boolean) ?: default

    private class MiniJsonParser(private val src: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val ch = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected char '$ch' at $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }

                    else -> throw IllegalArgumentException("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }

                    else -> throw IllegalArgumentException("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                when (val ch = src[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) throw IllegalArgumentException("Unterminated escape")
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) throw IllegalArgumentException("Invalid unicode escape")
                                val hex = src.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }

                            else -> throw IllegalArgumentException("Invalid escape '\\$esc'")
                        }
                    }

                    else -> sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && src[pos] in '0'..'9') pos++
            var isFloat = false
            if (pos < src.length && src[pos] == '.') {
                isFloat = true
                pos++
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            val token = src.substring(start, pos)
            return if (isFloat) token.toDouble() else token.toLong()
        }

        private fun parseBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else if (src.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                throw IllegalArgumentException("Invalid boolean at $pos")
            }
        }

        private fun parseNull(): Any? {
            if (src.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw IllegalArgumentException("Invalid null at $pos")
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char {
            if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
            return src[pos]
        }

        private fun expect(expected: Char) {
            if (pos >= src.length || src[pos] != expected) {
                throw IllegalArgumentException("Expected '$expected' at $pos")
            }
            pos++
        }
    }
}
