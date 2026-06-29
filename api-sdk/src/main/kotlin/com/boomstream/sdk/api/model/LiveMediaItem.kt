package com.boomstream.sdk.api.model

/** A live broadcast entry returned by the `api/live/folder` listing endpoint. */
data class LiveMediaItem(
    val code: String,
    val title: String = "",
    val poster: String? = null,
)
