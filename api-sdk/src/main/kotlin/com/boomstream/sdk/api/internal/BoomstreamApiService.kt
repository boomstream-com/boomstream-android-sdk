package com.boomstream.sdk.api.internal

import com.boomstream.sdk.api.internal.model.FolderApiRequest
import com.boomstream.sdk.api.internal.model.FolderApiResponse
import com.boomstream.sdk.api.internal.model.LiveFolderApiResponse
import com.boomstream.sdk.api.internal.model.PlaylistListApiResponse
import retrofit2.http.Body
import retrofit2.http.POST

internal interface BoomstreamApiService {

    @POST("api/media/folder")
    suspend fun listFolder(@Body request: FolderApiRequest): FolderApiResponse

    @POST("api/live/folder")
    suspend fun listLive(@Body request: FolderApiRequest): LiveFolderApiResponse

    @POST("api/playlist/list")
    suspend fun listPlaylists(@Body request: FolderApiRequest): PlaylistListApiResponse
}
