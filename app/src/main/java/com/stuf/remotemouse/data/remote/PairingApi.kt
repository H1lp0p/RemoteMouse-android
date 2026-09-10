package com.stuf.remotemouse.data.remote

import com.stuf.remotemouse.data.remote.dto.PairingRequestDto
import com.stuf.remotemouse.data.remote.dto.PairingResponseDto
import com.stuf.remotemouse.data.remote.dto.StatusResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface PairingApi {
    @POST("pair")
    suspend fun pair(@Body body: PairingRequestDto): Response<PairingResponseDto>

    @GET("status")
    suspend fun status(): Response<StatusResponseDto>
}
