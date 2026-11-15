package com.veil.privacy.trc.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** Ephemeral values only. Never serialize, log, or send these to a relay. */
data class TrcLocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtEpochMillis: Long
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
        require(accuracyMeters >= 0f)
    }
}

data class TrcDistanceEstimate(
    val meters: Double,
    val estimatedUncertaintyMeters: Double
)

/**
 * Distance-only sharing requires mutual consent and exchanges a coarse fix
 * inside the authenticated E2EE peer session. This is NOT private distance
 * computation: the peer can infer the approximate shared location.
 */
object TrcLocationPrivacy {
    private const val COORDINATE_SCALE = 1_000.0
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun coarseForMutualSharing(fix: TrcLocationFix): TrcLocationFix = fix.copy(
        latitude = round(fix.latitude * COORDINATE_SCALE) / COORDINATE_SCALE,
        longitude = round(fix.longitude * COORDINATE_SCALE) / COORDINATE_SCALE
    )

    fun estimateDistance(first: TrcLocationFix, second: TrcLocationFix): TrcDistanceEstimate {
        val lat1 = Math.toRadians(first.latitude)
        val lat2 = Math.toRadians(second.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(second.longitude - first.longitude)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        val centralAngle = 2 * atan2(sqrt(a), sqrt(1 - a))
        return TrcDistanceEstimate(
            meters = EARTH_RADIUS_METERS * centralAngle,
            estimatedUncertaintyMeters =
                first.accuracyMeters.toDouble() + second.accuracyMeters.toDouble()
        )
    }
}
