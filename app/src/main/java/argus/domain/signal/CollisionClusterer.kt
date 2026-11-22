package argus.domain.signal

/**
 * 기기명 미노출(UNKNOWN_BLE) 및 제조사 데이터 부재로 동일 지문이 충돌하는 엣지 케이스를 완화하기 위한 클러스터러.
 *
 * 동일 baseFingerprint로 들어온 패킷이라도 광고 주기(Advertising Interval)와
 * Kalman 필터링된 RSSI 궤적이 뚜렷하게 다르면 별도의 보조 클러스터(#1, #2, ...)로 분리한다.
 */
class CollisionClusterer(
    private val rssiJumpThresholdDb: Double = 14.0,
    private val intervalToleranceRatio: Double = 0.45,
    private val staleClusterTimeoutMs: Long = 30_000L
) {
    private data class ClusterTrack(
        val clusterKey: String, val kalmanFilter: RssiKalmanFilter, var lastSeenMs: Long, var avgIntervalMs: Double?
    )

    private val tracksByBase = mutableMapOf<String, MutableList<ClusterTrack>>()

    data class ClusterResolution(
        val clusterKey: String, val filteredRssi: Double, val estimatedIntervalMs: Long?
    )

    fun resolveCluster(
        baseFingerprint: String, isGenericUnknown: Boolean, rawRssi: Int, timestampMs: Long
    ): ClusterResolution {
        val tracks = tracksByBase.getOrPut(baseFingerprint) { mutableListOf() }

        // 비충돌(식별 가능한) 기기는 단일 트랙으로만 관리
        if (!isGenericUnknown) {
            val single = tracks.firstOrNull() ?: ClusterTrack(
                clusterKey = baseFingerprint,
                kalmanFilter = RssiKalmanFilter(),
                lastSeenMs = timestampMs,
                avgIntervalMs = null
            ).also { tracks.add(it) }

            val delta = updateInterval(single, timestampMs)
            val filtered = single.kalmanFilter.update(rawRssi.toDouble())
            single.lastSeenMs = timestampMs
            return ClusterResolution(single.clusterKey, filtered, delta)
        }

        // 오래된 클러스터 정리
        tracks.removeAll { timestampMs - it.lastSeenMs > staleClusterTimeoutMs }

        // 기존 트랙 중 RSSI 궤적과 광고 주기가 가장 잘 맞는 트랙 탐색
        var bestMatch: ClusterTrack? = null
        var bestScore = Double.MAX_VALUE

        for (track in tracks) {
            val rssiDiff = track.kalmanFilter.distanceFromEstimate(rawRssi.toDouble())
            if (rssiDiff > rssiJumpThresholdDb) continue

            val dt = (timestampMs - track.lastSeenMs).coerceAtLeast(1L).toDouble()
            val expectedInterval = track.avgIntervalMs
            if (expectedInterval != null && dt > 50L) {
                val ratioDiff = kotlin.math.abs(dt - expectedInterval) / expectedInterval
                // 정수배(패킷 유실) 허용: 1배 또는 2배 근처가 아니면 다른 기기 가능성
                val halfRatioDiff = kotlin.math.abs(dt - expectedInterval * 2.0) / (expectedInterval * 2.0)
                if (ratioDiff > intervalToleranceRatio && halfRatioDiff > intervalToleranceRatio) {
                    continue
                }
            }

            if (rssiDiff < bestScore) {
                bestScore = rssiDiff
                bestMatch = track
            }
        }

        val targetTrack = bestMatch ?: ClusterTrack(
            clusterKey = "${baseFingerprint}#${tracks.size + 1}",
            kalmanFilter = RssiKalmanFilter(),
            lastSeenMs = timestampMs,
            avgIntervalMs = null
        ).also { tracks.add(it) }

        val updatedInterval = updateInterval(targetTrack, timestampMs)
        val filtered = targetTrack.kalmanFilter.update(rawRssi.toDouble())
        targetTrack.lastSeenMs = timestampMs

        return ClusterResolution(
            clusterKey = targetTrack.clusterKey, filteredRssi = filtered, estimatedIntervalMs = updatedInterval
        )
    }

    fun reset() {
        tracksByBase.clear()
    }

    private fun updateInterval(track: ClusterTrack, timestampMs: Long): Long? {
        val dt = timestampMs - track.lastSeenMs
        if (dt in 20L..5000L) {
            track.avgIntervalMs = track.avgIntervalMs?.let { prev ->
                prev * 0.7 + dt * 0.3
            } ?: dt.toDouble()
        }
        return track.avgIntervalMs?.toLong()
    }
}
