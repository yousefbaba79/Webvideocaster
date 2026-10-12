package com.webvideocaster.app.browser

import java.net.URLEncoder

/** Turns whatever the user typed in the address bar into a URL to load. */
object UrlInput {
    const val HOME_URL = "https://www.google.com/"
    private const val SEARCH_URL = "https://www.google.com/search?q="

    fun toUrl(input: String): String {
        val text = input.trim()
        return when {
            text.isEmpty() -> HOME_URL
            text.contains("://") -> text
            !text.contains(' ') && text.contains('.') -> "https://$text"
            else -> SEARCH_URL + URLEncoder.encode(text, "UTF-8")
        }
    }
}
