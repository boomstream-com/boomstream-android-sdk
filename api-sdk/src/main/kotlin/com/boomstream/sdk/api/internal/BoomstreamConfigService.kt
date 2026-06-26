package com.boomstream.sdk.api.internal

import com.boomstream.sdk.api.model.ConfigResponse
import retrofit2.http.GET
import retrofit2.http.Path

internal interface BoomstreamConfigService {

    @GET("{mediaCode}/config")
    suspend fun getConfig(@Path("mediaCode") mediaCode: String): ConfigResponse
}
