package com.boomstream.sdk.player.internal

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.boomstream.sdk.player.BoomstreamPlayerStyle
import com.boomstream.sdk.player.VideoQuality

/**
 * Internal bottom-sheet-style [Dialog] surfacing Speed, Audio (when > 1 track), and Quality
 * (when [enableQualitySelector] is true) in a single panel.
 *
 * Shown when the user taps the Media3 `exo_settings` button — replaces the native Media3
 * settings dialog so Quality can live alongside Speed and Audio in one place.
 *
 * Themed via [BoomstreamPlayerStyle.accentColor]: selected row text and indicator use the
 * accent colour; unset defaults to Boomstream violet `#662BFF`.
 */
@OptIn(UnstableApi::class)
internal class BoomstreamSettingsSheet(
    context: Context,
    private val player: BoomstreamMediaPlayer,
    private val style: BoomstreamPlayerStyle?,
    private val enableQualitySelector: Boolean,
) : Dialog(context) {

    private val dm = context.resources.displayMetrics
    private fun dp(value: Float) = (value * dm.density).toInt()

    private val accentColor: Int get() = style?.accentColor ?: Color.parseColor("#662BFF")
    private val locale: String? get() = player.locale

    init {
        window?.requestFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(buildContent())
        window?.apply {
            setGravity(Gravity.BOTTOM)
            setLayout(MATCH_PARENT, WRAP_CONTENT)
        }
    }

    private fun buildContent(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8f), 0, dp(24f))
            background = GradientDrawable().also { bg ->
                bg.setColor(Color.parseColor("#1C1C1E"))
                val r = dp(16f).toFloat()
                bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }

        // Drag handle
        container.addView(buildDragHandle())

        // ── Speed ─────────────────────────────────────────────────────────────
        addSectionHeader(container, BoomstreamMessages.resolve("settings_speed", locale))
        val currentSpeed = player.playbackSpeed.value
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
            val label = if (speed == 1.0f) BoomstreamMessages.resolve("settings_speed_normal", locale) else "${speed}×"
            addRow(container, label, speed == currentSpeed) {
                player.setPlaybackSpeed(speed)
                dismiss()
            }
        }

        // ── Audio ─────────────────────────────────────────────────────────────
        val audioTracks = player.availableAudioTracks.value
        if (audioTracks.size > 1) {
            addDivider(container)
            addSectionHeader(container, BoomstreamMessages.resolve("settings_audio", locale))
            val currentAudio = player.currentAudioTrack.value
            audioTracks.forEach { track ->
                addRow(container, track.label, track == currentAudio) {
                    player.selectAudioTrack(track)
                    dismiss()
                }
            }
        }

        // ── Subtitles ─────────────────────────────────────────────────────────
        val subtitleTracks = player.availableSubtitleTracks.value
        if (subtitleTracks.isNotEmpty()) {
            addDivider(container)
            addSectionHeader(container, BoomstreamMessages.resolve("subtitles_title", locale))
            val currentSubtitle = player.currentSubtitleTrack.value
            addRow(container, BoomstreamMessages.resolve("subtitles_off", locale), currentSubtitle == null) {
                player.clearSubtitleTrack()
                dismiss()
            }
            subtitleTracks.forEach { track ->
                addRow(container, track.label, track == currentSubtitle) {
                    player.selectSubtitleTrack(track)
                    dismiss()
                }
            }
        }

        // ── Quality ───────────────────────────────────────────────────────────
        if (enableQualitySelector) {
            val qualities = player.availableQualities.value
            if (qualities.isNotEmpty()) {
                addDivider(container)
                addSectionHeader(container, BoomstreamMessages.resolve("settings_quality", locale))
                val currentQuality = player.currentQuality.value
                addRow(container, BoomstreamMessages.resolve("settings_quality_auto", locale), currentQuality is VideoQuality.Auto) {
                    player.selectAuto()
                    dismiss()
                }
                qualities.filterIsInstance<VideoQuality.Resolution>().forEach { q ->
                    addRow(container, q.label, currentQuality == q) {
                        player.selectQuality(q)
                        dismiss()
                    }
                }
            }
        }

        return ScrollView(context).apply {
            addView(container, MATCH_PARENT, WRAP_CONTENT)
        }
    }

    private fun buildDragHandle(): View {
        val handle = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#555558"))
                cornerRadius = dp(2f).toFloat()
            }
        }
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8f), 0, dp(4f))
            addView(handle, dp(40f), dp(4f))
        }
    }

    private fun addSectionHeader(container: LinearLayout, title: String) {
        container.addView(
            TextView(context).apply {
                text = title
                textSize = 11f
                letterSpacing = 0.08f
                setTextColor(Color.parseColor("#8E8E93"))
                setPadding(dp(20f), dp(10f), dp(20f), dp(4f))
            },
            MATCH_PARENT, WRAP_CONTENT,
        )
    }

    private fun addDivider(container: LinearLayout) {
        container.addView(
            View(context).apply { setBackgroundColor(Color.parseColor("#2C2C2E")) },
            MATCH_PARENT, dp(0.5f).coerceAtLeast(1),
        )
    }

    private fun addRow(
        container: LinearLayout,
        label: String,
        isSelected: Boolean,
        onClick: () -> Unit,
    ) {
        val accent = accentColor
        val textColor = if (isSelected) accent else Color.WHITE

        val indicator = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (isSelected) {
                    setColor(accent)
                } else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1.5f).coerceAtLeast(1), Color.parseColor("#555558"))
                }
            }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20f), 0, dp(20f), 0)
            minimumHeight = dp(48f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            // Ripple-like press feedback using the default selectable background.
            setBackgroundResource(android.R.drawable.list_selector_background)
        }

        val indicatorLp = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply {
            marginEnd = dp(14f)
        }
        row.addView(indicator, indicatorLp)

        row.addView(
            TextView(context).apply {
                text = label
                textSize = 15f
                setTextColor(textColor)
            },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )

        container.addView(row, MATCH_PARENT, WRAP_CONTENT)
    }
}
