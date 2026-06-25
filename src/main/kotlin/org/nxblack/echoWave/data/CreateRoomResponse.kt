package org.nxblack.echoWave.data

import kotlinx.serialization.Serializable

@Serializable
data class CreateRoomResponse(
    val success: Boolean,
    val code: String,
    val token: String
)
