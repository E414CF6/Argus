package argus.domain.signal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RssiKalmanFilterAndClusterTest {

    @Test
    fun `Kalman 필터는 일시적 RSSI 스파이크 노이즈를 완화한다`() {
        val filter = RssiKalmanFilter()
        filter.update(-60.0)
        filter.update(-61.0)
        val smoothedAfterSpike = filter.update(-90.0)

        assertThat(smoothedAfterSpike).isGreaterThan(-75.0)
        assertThat(smoothedAfterSpike).isLessThan(-60.0)
    }

    @Test
    fun `Generic UNKNOWN_BLE 기기 두 대가 다른 RSSI 궤적을 가지면 별도 클러스터로 분리된다`() {
        val clusterer = CollisionClusterer(rssiJumpThresholdDb = 12.0)

        // 기기 A: -50 dBm 근처, 500ms 간격
        val a1 = clusterer.resolveCluster("abcd1234", isGenericUnknown = true, rawRssi = -50, timestampMs = 1000L)
        // 기기 B: -74 dBm 근처 (24dB 차이 -> 분리되어야 함)
        val b1 = clusterer.resolveCluster("abcd1234", isGenericUnknown = true, rawRssi = -74, timestampMs = 1200L)
        // 기기 A 재관측: -51 dBm
        val a2 = clusterer.resolveCluster("abcd1234", isGenericUnknown = true, rawRssi = -51, timestampMs = 1500L)

        assertThat(a1.clusterKey).isEqualTo("abcd1234#1")
        assertThat(b1.clusterKey).isEqualTo("abcd1234#2")
        assertThat(a2.clusterKey).isEqualTo("abcd1234#1")
    }
}
