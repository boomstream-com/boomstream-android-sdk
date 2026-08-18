package com.boomstream.sdk.api.internal

import com.boomstream.sdk.api.model.ConfigResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

internal interface BoomstreamConfigService {

    // x-platform lets the media server pick platform-appropriate encryption/delivery (e.g. serve an
    // Android-playable scheme vs iOS SAMPLE-AES) until unified CMAF delivery lands.
    @Headers("x-platform: android")
    @GET("{mediaCode}/config")
    suspend fun getConfig(@Path("mediaCode") mediaCode: String): ConfigResponse
}
