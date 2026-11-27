package it.casatv.tv.ui

import it.casatv.tv.data.Channel
import java.util.Locale

private val AUDIO_EXT_REGEX =
    Regex("""\.(mp3|aac|aacp|ogg|oga|opus|flac|m4a|wav|wma|pls)(\?|#|$)""", RegexOption.IGNORE_CASE)

private val AUDIO_ENDPOINT_REGEX =
    Regex("""(/stream(\?|/|$)|;stream|\.(mp3|aac|ogg|opus|flac|m4a)(\?|$))""", RegexOption.IGNORE_CASE)

private val VIDEO_QUALITY_SET = setOf("HD", "FHD", "4K", "UHD", "1080P", "720P")

private val VIDEO_NAME_HINT_REGEX = Regex(
    """(?i)(^|[\s\-_|/(\[.])(tv|television|televisión|televisió|tele|bfm|vision|visió|video|vídeo|canal|channel|radiotv|radio-tv|visual\s*radio|radiovision|hd|uhd|fhd|4k|1080p|720p|576p|480p|360p|240p)([\s\-_|/)\].]|$)""",
)

private val RADIO_NAME_HINT_REGEX = Regex(
    """(?i)(^|[\s\-_|/(\[])(fm|am|radio|rádio|rádió|irratia|dab\+?|webradio)([\s\-_|/)\].]|$)""",
)

/**
 * Heuristic classifier identifying radio and audio-only streams before playback.
 * Runtime ExoPlayer track inspection catches remaining HLS/TS audio-only stations
 * whose metadata resembles TV channels (e.g. "Hír FM" inside a country category).
 */
fun isLikelyAudioOnlyChannel(channel: Channel): Boolean {
    val url = channel.streamUrl.trim()
    val urlLower = url.lowercase(Locale.ROOT)
    if (AUDIO_EXT_REGEX.containsMatchIn(urlLower)) return true
    val isContainerManifest = urlLower.contains(".m3u8") || urlLower.contains(".mpd") || urlLower.contains(".ts")
    if (!isContainerManifest && AUDIO_ENDPOINT_REGEX.containsMatchIn(urlLower)) return true

    val cats = channel.categories + channel.categoryNames
    if (cats.any { raw ->
            val c = raw.lowercase(Locale.ROOT)
            (c.contains("radio") || c.contains("irratia")) &&
                !c.contains("tv") &&
                !c.contains("video") &&
                !c.contains("tele")
        }
    ) {
        return true
    }

    val qualityUpper = channel.quality.trim().uppercase(Locale.ROOT)
    val hasVideoHint = qualityUpper in VIDEO_QUALITY_SET || VIDEO_NAME_HINT_REGEX.containsMatchIn(channel.name)
    return !hasVideoHint && RADIO_NAME_HINT_REGEX.containsMatchIn(channel.name)
}
