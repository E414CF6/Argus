package argus.domain.fingerprint

import argus.domain.model.RawBleSignal
import argus.domain.model.RawWifiSignal
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FingerprintGeneratorTest {

    @Test
    fun `BLE Apple AirPods 패킷은 가변 배터리 바이트가 달라도 동일한 지문을 생성한다`() {
        // Apple 0x004C, Continuity 0x07 (AirPods), 뒤쪽 바이트는 가변 배터리/카운터
        val packet1 = RawBleSignal(
            deviceName = null,
            serviceUuids16Bit = emptyList(),
            companyId = 0x004C,
            manufacturerData = byteArrayOf(0x07, 0x19, 0x01, 0x02, 0x64),
            rssi = -58,
            timestampMs = 1000L
        )
        val packet2 = RawBleSignal(
            deviceName = null,
            serviceUuids16Bit = emptyList(),
            companyId = 0x004C,
            manufacturerData = byteArrayOf(0x07, 0x19, 0x99.toByte(), 0x55, 0x50),
            rssi = -62,
            timestampMs = 2000L
        )

        val fp1 = FingerprintGenerator.generateBle(packet1)
        val fp2 = FingerprintGenerator.generateBle(packet2)

        assertThat(fp1.fingerprint).isEqualTo(fp2.fingerprint)
        assertThat(fp1.manufacturer).isEqualTo("Apple")
        assertThat(fp1.isSuspiciousAudioOrWearable).isTrue()
        assertThat(fp1.displayName).isEqualTo("UNKNOWN_BLE")
    }

    @Test
    fun `BLE Service UUID 순서가 달라도 정규화되어 동일한 지문을 생성한다`() {
        val sig1 = RawBleSignal("Watch", listOf("180D", "180F"), 0x0075, byteArrayOf(0x42, 0x04), -60, 1000L)
        val sig2 = RawBleSignal("Watch", listOf("180f", "180d"), 0x0075, byteArrayOf(0x42, 0x04), -61, 2000L)

        val fp1 = FingerprintGenerator.generateBle(sig1)
        val fp2 = FingerprintGenerator.generateBle(sig2)

        assertThat(fp1.fingerprint).isEqualTo(fp2.fingerprint)
        assertThat(fp1.isSuspiciousAudioOrWearable).isTrue()
    }

    @Test
    fun `Wi-Fi 개인 핫스팟은 SSID와 대역, 보안방식, OUI로 지문을 생성하고 의심 플래그를 켠다`() {
        val hotspot = RawWifiSignal(
            ssid = "홍길동의 iPhone",
            bssid = "A4:83:E7:11:22:33",
            capabilities = "[WPA2-PSK-CCMP][ESS]",
            frequencyMhz = 5180,
            rssi = -52,
            timestampMs = 1000L
        )

        val fp = FingerprintGenerator.generateWifi(hotspot)
        assertThat(fp.displayName).isEqualTo("홍길동의 iPhone (5GHz)")
        assertThat(fp.manufacturer).isEqualTo("OUI_A4:83:E7")
        assertThat(fp.isSuspiciousAudioOrWearable).isTrue()
        assertThat(fp.fingerprint).hasLength(16)
    }
}
