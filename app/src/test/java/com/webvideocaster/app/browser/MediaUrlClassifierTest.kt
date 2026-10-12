package com.webvideocaster.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUrlClassifierTest {
    @Test
    fun detectsCommonVideoExtensions() {
        assertEquals("video/mp4", MediaUrlClassifier.classify("https://cdn.example.com/movies/clip.mp4"))
        assertEquals("video/mp4", MediaUrlClassifier.classify("https://cdn.example.com/clip.MP4?token=abc#t=10"))
        assertEquals("video/webm", MediaUrlClassifier.classify("http://example.com/a/b.webm"))
        assertEquals(MediaUrlClassifier.HLS, MediaUrlClassifier.classify("https://example.com/live/master.m3u8?sig=1"))
        assertEquals(MediaUrlClassifier.DASH, MediaUrlClassifier.classify("https://example.com/stream/manifest.mpd"))
    }

    @Test
    fun detectsExtensionFollowedByPathSegment() {
        assertEquals(MediaUrlClassifier.HLS, MediaUrlClassifier.classify("https://example.com/hls/video.m3u8/index"))
    }

    @Test
    fun detectsVideoFileInQueryString() {
        assertEquals("video/mp4", MediaUrlClassifier.classify("https://example.com/player?file=video.mp4&autoplay=1"))
    }

    @Test
    fun ignoresNonVideoUrls() {
        assertNull(MediaUrlClassifier.classify("https://example.com/index.html"))
        assertNull(MediaUrlClassifier.classify("https://example.com/segment.ts"))
        assertNull(MediaUrlClassifier.classify("https://example.com/mp4-guide"))
        assertNull(MediaUrlClassifier.classify("https://example.com/"))
    }

    @Test
    fun ignoresExtensionInHostName() {
        assertNull(MediaUrlClassifier.classify("https://trailers.mov/watch"))
        assertNull(MediaUrlClassifier.classify("https://trailers.mov"))
    }

    @Test
    fun ignoresUncastableSchemes() {
        assertNull(MediaUrlClassifier.classify("blob:https://example.com/1234-abcd"))
        assertNull(MediaUrlClassifier.classify("data:video/mp4;base64,AAAA"))
        assertNull(MediaUrlClassifier.classify("file:///sdcard/video.mp4"))
    }

    @Test
    fun mapsContentTypes() {
        assertEquals(MediaUrlClassifier.HLS, MediaUrlClassifier.mimeFromContentType("application/vnd.apple.mpegurl"))
        assertEquals(MediaUrlClassifier.HLS, MediaUrlClassifier.mimeFromContentType("application/x-mpegURL"))
        assertEquals("video/mp4", MediaUrlClassifier.mimeFromContentType("video/mp4; codecs=\"avc1.42E01E\""))
        assertEquals(MediaUrlClassifier.DASH, MediaUrlClassifier.mimeFromContentType("application/dash+xml"))
        assertNull(MediaUrlClassifier.mimeFromContentType("text/html"))
        assertNull(MediaUrlClassifier.mimeFromContentType(""))
        assertNull(MediaUrlClassifier.mimeFromContentType(null))
    }

    @Test
    fun detectedVideoNames() {
        val video = DetectedVideo("https://cdn.example.com/path/my-clip.mp4?x=1", "video/mp4")
        assertEquals("my-clip.mp4", video.fileName)
        assertEquals("cdn.example.com", video.host)
        assertEquals("my-clip.mp4", video.displayName)
        assertEquals("Page title", video.copy(title = "Page title").displayName)
        assertEquals("MP4", video.formatLabel)
    }
}
