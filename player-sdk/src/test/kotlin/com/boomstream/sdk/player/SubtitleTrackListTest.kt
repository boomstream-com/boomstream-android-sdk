package com.boomstream.sdk.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.boomstream.sdk.player.internal.SubtitleTrackInfo
import com.boomstream.sdk.player.internal.buildSubtitleTrackList
import com.boomstream.sdk.player.internal.detectCurrentSubtitleTrack
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleTrackListTest {

    private fun textFormat(id: String, language: String? = null, label: String? = null): Format =
        Format.Builder()
            .setId(id)
            .setLanguage(language)
            .setLabel(label)
            .setSampleMimeType(MimeTypes.TEXT_VTT)
            .build()

    private fun captionFormat(id: String, mimeType: String): Format =
        Format.Builder()
            .setId(id)
            .setSampleMimeType(mimeType)
            .build()

    private fun subtitleGroup(format: Format, selected: Boolean): Tracks.Group =
        Tracks.Group(
            TrackGroup(format),
            /* adaptiveSupported */ false,
            intArrayOf(C.FORMAT_HANDLED),
            booleanArrayOf(selected),
        )

    private fun tracksWithSubtitles(vararg pairs: Pair<String, Boolean>): Tracks {
        val groups = pairs.mapIndexed { i, (lang, selected) ->
            subtitleGroup(textFormat("sub-$i", language = lang), selected)
        }
        return Tracks(ImmutableList.copyOf(groups))
    }

    // ── buildSubtitleTrackList ────────────────────────────────────────────────

    @Test
    fun `returns empty list when tracks has no groups`() {
        assertEquals(
            emptyList<SubtitleTrackInfo>(),
            buildSubtitleTrackList(Tracks(ImmutableList.of())),
        )
    }

    @Test
    fun `maps language to uppercase label when no explicit label`() {
        val tracks = tracksWithSubtitles("en" to false, "ru" to false)
        val result = buildSubtitleTrackList(tracks)
        assertEquals(2, result.size)
        assertEquals("EN", result[0].label)
        assertEquals("RU", result[1].label)
    }

    @Test
    fun `prefers explicit format label over language`() {
        val format = textFormat("sub-0", language = "en", label = "English Subtitles")
        val tracks = Tracks(ImmutableList.of(subtitleGroup(format, false)))
        val result = buildSubtitleTrackList(tracks)
        assertEquals("English Subtitles", result[0].label)
    }

    @Test
    fun `assigns numbered fallback when format carries no language or label`() {
        val format = textFormat("sub-0")
        val tracks = Tracks(ImmutableList.of(subtitleGroup(format, false)))
        val result = buildSubtitleTrackList(tracks)
        assertEquals("Subtitle 1", result[0].label)
    }

    @Test
    fun `groupIndex and trackIndex match position in tracks groups`() {
        val tracks = tracksWithSubtitles("en" to false, "ru" to false)
        val result = buildSubtitleTrackList(tracks)
        assertEquals(0, result[0].groupIndex)
        assertEquals(0, result[0].trackIndex)
        assertEquals(1, result[1].groupIndex)
        assertEquals(0, result[1].trackIndex)
    }

    // ── CEA-608 / CEA-708 filtering ───────────────────────────────────────────

    @Test
    fun `CEA-608 tracks are excluded from subtitle list`() {
        val format = captionFormat("cc-0", MimeTypes.APPLICATION_CEA608)
        val tracks = Tracks(ImmutableList.of(subtitleGroup(format, false)))
        assertEquals(emptyList<SubtitleTrackInfo>(), buildSubtitleTrackList(tracks))
    }

    @Test
    fun `CEA-708 tracks are excluded from subtitle list`() {
        val format = captionFormat("cc-0", MimeTypes.APPLICATION_CEA708)
        val tracks = Tracks(ImmutableList.of(subtitleGroup(format, false)))
        assertEquals(emptyList<SubtitleTrackInfo>(), buildSubtitleTrackList(tracks))
    }

    @Test
    fun `WebVTT track is included while CEA-608 track in same Tracks is excluded`() {
        val cea608Group = subtitleGroup(captionFormat("cc-0", MimeTypes.APPLICATION_CEA608), false)
        val vttGroup = subtitleGroup(textFormat("sub-0", language = "en"), false)
        val tracks = Tracks(ImmutableList.of(cea608Group, vttGroup))
        val result = buildSubtitleTrackList(tracks)
        assertEquals(1, result.size)
        assertEquals("EN", result[0].label)
        assertEquals(1, result[0].groupIndex)
        assertEquals(0, result[0].trackIndex)
    }

    // ── detectCurrentSubtitleTrack ────────────────────────────────────────────

    @Test
    fun `returns null for empty subtitle list`() {
        assertNull(detectCurrentSubtitleTrack(Tracks(ImmutableList.of()), emptyList()))
    }

    @Test
    fun `returns null when no subtitle track is selected`() {
        val tracks = tracksWithSubtitles("en" to false, "ru" to false)
        val list = buildSubtitleTrackList(tracks)
        assertNull(detectCurrentSubtitleTrack(tracks, list))
    }

    @Test
    fun `returns the currently selected subtitle track`() {
        val tracks = tracksWithSubtitles("en" to false, "ru" to true)
        val list = buildSubtitleTrackList(tracks)
        val current = detectCurrentSubtitleTrack(tracks, list)
        assertEquals("RU", current?.label)
        assertEquals(1, current?.groupIndex)
    }
}
