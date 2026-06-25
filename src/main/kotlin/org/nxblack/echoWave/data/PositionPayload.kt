package org.nxblack.echoWave.data

import kotlinx.serialization.Serializable

@Serializable
data class PositionPayload(
    val gamertag: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val dimension: String,
    val groupId: String?,
    val isMuted: Boolean
)