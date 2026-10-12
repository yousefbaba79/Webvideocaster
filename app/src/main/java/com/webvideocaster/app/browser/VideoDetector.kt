package com.webvideocaster.app.browser

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Collects the videos found on the current page, from two sources:
 * network requests made by the page (catches HLS/DASH playlists and iframes)
 * and `<video>` elements reported by [INJECTED_SCRIPT].
 *
 * All state is touched on the main thread; [onRequest] and the JS bridge may be called from others.
 */
class VideoDetector(private val onChanged: (List<DetectedVideo>) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val videos = LinkedHashMap<String, DetectedVideo>()
    private var pageUrl: String? = null
    private var pageTitle: String? = null

    val bridge = Bridge()

    val currentVideos: List<DetectedVideo>
        get() = videos.values.toList()

    fun onPageStarted(url: String?) {
        pageUrl = url
        pageTitle = null
        videos.clear()
        onChanged(currentVideos)
    }

    fun onTitle(title: String?) {
        pageTitle = title
        if (title.isNullOrBlank()) return
        for (entry in videos.entries) {
            if (entry.value.title == null) entry.setValue(entry.value.copy(title = title))
        }
        onChanged(currentVideos)
    }

    /** Called for every network request the page makes, on a WebView background thread. */
    fun onRequest(url: String) {
        val mime = MediaUrlClassifier.classify(url) ?: return
        post(url, mime, null)
    }

    fun onDomVideo(url: String, typeHint: String?, title: String?) {
        if (!MediaUrlClassifier.isCastableScheme(url)) return
        val mime = MediaUrlClassifier.mimeFromContentType(typeHint)
            ?: MediaUrlClassifier.classify(url)
            ?: MediaUrlClassifier.MP4
        post(url, mime, title)
    }

    /** Adds a video directly, e.g. one shared to the app or typed in by the user. Main thread only. */
    fun add(video: DetectedVideo) {
        videos[video.url] = video
        onChanged(currentVideos)
    }

    private fun post(url: String, mime: String, title: String?) {
        mainHandler.post {
            if (url !in videos) {
                videos[url] = DetectedVideo(url, mime, title?.takeIf { it.isNotBlank() } ?: pageTitle, pageUrl)
                onChanged(currentVideos)
            }
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun onVideoFound(url: String?, type: String?, title: String?) {
            if (url != null) onDomVideo(url, type, title)
        }
    }

    companion object {
        const val JS_BRIDGE_NAME = "WebVideoCasterBridge"

        /** Reports `<video>` sources to [Bridge], now and whenever the page changes. Safe to inject repeatedly. */
        val INJECTED_SCRIPT = """
            (function() {
              if (window.__wvcScan) { window.__wvcScan(); return; }
              function report(src, type) {
                if (!src || src.indexOf('blob:') === 0 || src.indexOf('data:') === 0) return;
                try {
                  $JS_BRIDGE_NAME.onVideoFound(new URL(src, document.baseURI).href, type || '', document.title || '');
                } catch (e) {}
              }
              function onMedia() { report(this.currentSrc || this.src, ''); }
              function scan() {
                var videos = document.querySelectorAll('video');
                for (var i = 0; i < videos.length; i++) {
                  var v = videos[i];
                  report(v.currentSrc || v.getAttribute('src'), v.getAttribute('type'));
                  var sources = v.querySelectorAll('source');
                  for (var j = 0; j < sources.length; j++) report(sources[j].src, sources[j].type);
                  if (!v.__wvc) {
                    v.__wvc = true;
                    v.addEventListener('loadstart', onMedia);
                    v.addEventListener('play', onMedia);
                  }
                }
              }
              window.__wvcScan = scan;
              scan();
              var timer = null;
              new MutationObserver(function() {
                clearTimeout(timer);
                timer = setTimeout(scan, 500);
              }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['src'] });
            })();
        """.trimIndent()
    }
}
