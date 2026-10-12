package com.webvideocaster.app.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlInputTest {
    @Test
    fun keepsFullUrls() {
        assertEquals("http://example.com/a", UrlInput.toUrl("  http://example.com/a "))
    }

    @Test
    fun addsHttpsToBareDomains() {
        assertEquals("https://example.com", UrlInput.toUrl("example.com"))
    }

    @Test
    fun searchesEverythingElse() {
        assertEquals("https://www.google.com/search?q=funny+cat+videos", UrlInput.toUrl("funny cat videos"))
    }

    @Test
    fun emptyGoesHome() {
        assertEquals(UrlInput.HOME_URL, UrlInput.toUrl("   "))
    }
}
