package com.boomstream.sdk.player.internal

/**
 * Internal message table for system-message localisation (ru / en).
 *
 * Locale is supplied by the integrator at player initialisation time, not inferred from the
 * system locale, so Android resource qualifiers (`values-ru/`) would not honour the caller's
 * explicit choice. This table is the preferred approach.
 *
 * Keys match the `message` field of the server's `accessRestricted` object and the values
 * used for other system states (stream_offline, playing_record, no_network_offline).
 */
internal object BoomstreamMessages {

    private val translations: Map<String, Map<String, String>> = mapOf(
        "ru" to mapOf(
            "stream_offline" to "Трансляция оффлайн",
            "playing_record" to "Играет запись",
            "no_network_offline" to "Нет сети и оффлайн контент не загружен",
            "access_restricted" to "Доступ к контенту ограничен",
            "settings_speed" to "Скорость",
            "settings_audio" to "Аудио",
            "settings_quality" to "Качество",
            "settings_speed_normal" to "Обычная (1×)",
            "settings_quality_auto" to "Авто",
            "subtitles_title" to "Субтитры",
            "subtitles_off" to "Выкл.",
        ),
        "en" to mapOf(
            "stream_offline" to "Stream is offline",
            "playing_record" to "Playing recording",
            "no_network_offline" to "No network and offline content is not downloaded",
            "access_restricted" to "Access to this content is restricted",
            "settings_speed" to "Speed",
            "settings_audio" to "Audio",
            "settings_quality" to "Quality",
            "settings_speed_normal" to "Normal (1×)",
            "settings_quality_auto" to "Auto",
            "subtitles_title" to "Subtitles",
            "subtitles_off" to "Off",
        ),
    )

    /**
     * Resolve [key] for the given [locale].
     *
     * Lookup order:
     * 1. `translations[locale][key]`
     * 2. `translations["en"][key]` (English fallback)
     * 3. [fallback] (caller-supplied fallback, e.g. the server's `translate` field)
     * 4. [key] itself (last resort — never blank)
     */
    fun resolve(key: String, locale: String?, fallback: String? = null): String =
        (locale?.let { translations[it]?.get(key) })
            ?: translations["en"]?.get(key)
            ?: fallback
            ?: key
}
