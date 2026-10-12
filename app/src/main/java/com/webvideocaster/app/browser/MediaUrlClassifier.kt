package com.webvideocaster.app.browser

/** Decides whether a URL (or a declared content type) points to a castable video stream. */
object MediaUrlClassifier {
    const val MP4 = "video/mp4"
    const val HLS = "application/x-mpegURL"
    const val DASH = "application/dash+xml"

    private val extensionMimeTypes = mapOf(
        "mp4" to MP4,
        "m4v" to MP4,
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "mov" to "video/quicktime",
        "m3u8" to HLS,
        "mpd" to DASH,
    )

    private val pathExtension = Regex("""\.(mp4|m4v|webm|mkv|mov|m3u8|mpd)(?=$|[/;])""", RegexOption.IGNORE_CASE)
    private val queryExtension = Regex("""\.(mp4|m4v|webm|m3u8|mpd)(?=$|[&/;])""", RegexOption.IGNORE_CASE)

    fun isCastableScheme(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    /** Returns the MIME type if [url] looks like a video stream, or null otherwise. */
    fun classify(url: String): String? {
        if (!isCastableScheme(url)) return null
        val afterScheme = url.substringAfter("://").substringBefore('#')
        val pathStart = afterScheme.indexOfAny(charArrayOf('/', '?'))
        if (pathStart < 0) return null
        val pathAndQuery = afterScheme.substring(pathStart)
        val path = pathAndQuery.substringBefore('?')
        val query = pathAndQuery.substringAfter('?', "")

        val match = pathExtension.findAll(path).lastOrNull()
            ?: queryExtension.findAll(query).lastOrNull()
            ?: return null
        return extensionMimeTypes[match.groupValues[1].lowercase()]
    }

    /** Maps a `type` attribute or Content-Type header to a castable MIME type, if it is one. */
    fun mimeFromContentType(contentType: String?): String? {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        return when {
            type.isNullOrEmpty() -> null
            type.contains("mpegurl") -> HLS
            type == DASH -> DASH
            type.startsWith("video/") -> type
            else -> null
        }
    }

    fun formatLabel(mimeType: String): String = when (mimeType) {
        HLS -> "HLS"
        DASH -> "DASH"
        MP4 -> "MP4"
        "video/webm" -> "WEBM"
        "video/x-matroska" -> "MKV"
        "video/quicktime" -> "MOV"
        else -> "VIDEO"
    }
}
