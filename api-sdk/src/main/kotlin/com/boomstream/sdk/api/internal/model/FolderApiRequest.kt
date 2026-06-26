package com.boomstream.sdk.api.internal.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Request body for `POST /api/media/folder`.
 *
 * `apikey` auth is handled via `Authorization: Bearer` header by
 * [com.boomstream.sdk.api.internal.interceptor.BearerAuthInterceptor].
 * Null optional params are excluded via [EncodeDefault.Mode.NEVER].
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class FolderApiRequest(
    val ver: String = "1.2",
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val code: String? = null,
)
