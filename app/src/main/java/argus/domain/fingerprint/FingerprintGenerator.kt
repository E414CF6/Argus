package argus.domain.fingerprint

import argus.domain.model.RawBleSignal
import argus.domain.model.RawWifiSignal
import java.security.MessageDigest

data class FingerprintResult(
    val fingerprint: String,
    val displayName: String,
    val manufacturer: String,
    val isSuspiciousAudioOrWearable: Boolean,
    val isGenericUnknown: Boolean
)

/**
 * MAC 랜덤화를 우회하기 위해 변하지 않는 패킷 메타데이터를 SHA-256으로 해싱하는 지문 생성기.
 */
object FingerprintGenerator {

    private val AUDIO_WEARABLE_UUIDS = setOf("180D", "110B", "111E", "184E", "FE2C")
    private val HOTSPOT_KEYWORDS = listOf("iphone", "galaxy", "android", "pixel", "hotspot", "ipad")

    fun generateBle(signal: RawBleSignal): FingerprintResult {
        val name = signal.deviceName?.trim()?.takeIf { it.isNotEmpty() } ?: "UNKNOWN_BLE"
        val sortedUuids = signal.serviceUuids16Bit.map { it.uppercase() }.distinct().sorted().joinToString(",")

        val manufacturerTag = resolveManufacturerTag(signal.companyId)
        val rawHeaderHex = signal.manufacturerData?.take(2)?.joinToString("") { "%02X".format(it) }.orEmpty()

        val isAppleAudio = signal.companyId == 0x004C && rawHeaderHex.startsWith("07")
        val hasAudioOrWearableUuid = signal.serviceUuids16Bit.any { it.uppercase() in AUDIO_WEARABLE_UUIDS }
        val isSuspicious = isAppleAudio || hasAudioOrWearableUuid

        val isGenericUnknown = (name == "UNKNOWN_BLE" && signal.companyId == null && sortedUuids.isEmpty())

        val rawSignature = "BLE|$name|$manufacturerTag|$rawHeaderHex|$sortedUuids"
        val fingerprint = sha256Short(rawSignature)

        return FingerprintResult(
            fingerprint = fingerprint,
            displayName = name,
            manufacturer = manufacturerTag,
            isSuspiciousAudioOrWearable = isSuspicious,
            isGenericUnknown = isGenericUnknown
        )
    }

    fun generateWifi(signal: RawWifiSignal): FingerprintResult {
        val ssid = signal.ssid?.trim()?.takeIf { it.isNotEmpty() } ?: "HIDDEN_SSID"
        val band = when {
            signal.frequencyMhz < 3000 -> "2.4GHz"
            signal.frequencyMhz < 5925 -> "5GHz"
            else -> "6GHz"
        }
        val normalizedCaps =
            signal.capabilities.uppercase().split("[", "]").filter { it.isNotBlank() }.sorted().joinToString("-")

        val oui =
            signal.bssid?.uppercase()?.split(":")?.take(3)?.takeIf { it.size == 3 }?.joinToString(":") ?: "00:00:00"

        val lowerSsid = ssid.lowercase()
        val isHotspot = HOTSPOT_KEYWORDS.any { lowerSsid.contains(it) }

        val rawSignature = "WIFI|$ssid|$normalizedCaps|$band|$oui"
        val fingerprint = sha256Short(rawSignature)

        return FingerprintResult(
            fingerprint = fingerprint,
            displayName = "$ssid ($band)",
            manufacturer = "OUI_$oui",
            isSuspiciousAudioOrWearable = isHotspot,
            isGenericUnknown = (ssid == "HIDDEN_SSID")
        )
    }

    private fun resolveManufacturerTag(companyId: Int?): String = when (companyId) {
        null -> "Generic"
        0x004C -> "Apple"
        0x0075 -> "Samsung"
        0x00E0 -> "Google"
        0x0006 -> "Microsoft"
        0x012D -> "Sony"
        else -> "ID_0x%04X".format(companyId)
    }

    fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
