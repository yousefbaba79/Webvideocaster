package com.webvideocaster.app.browser

import java.net.URI

/** A video stream found on a web page that can be sent to a TV. */
data class DetectedVideo(
    val url: String,
    val mimeType: String,
    val title: String? = null,
    val pageUrl: String? = null,
) {
    val fileName: String
        get() = url.substringBefore('#').substringBefore('?').trimEnd('/')
            .substringAfterLast('/').ifBlank { host }

    val host: String
        get() = runCatching { URI(url).host }.getOrNull().orEmpty()

    val displayName: String
        get() = title?.takeIf { it.isNotBlank() } ?: fileName

    val formatLabel: String
        get() = MediaUrlClassifier.formatLabel(mimeType)
}
