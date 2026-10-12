package com.webvideocaster.app.cast

import android.content.Context
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.webvideocaster.app.browser.DetectedVideo

/** Loads videos onto a Chromecast, waiting for the session to connect when needed. */
class ChromecastController(context: Context) {
    /** Null on devices without Google Play services; casting then falls back to DLNA only. */
    val castContext: CastContext? = try {
        CastContext.getSharedInstance(context.applicationContext)
    } catch (e: Exception) {
        Log.w(TAG, "Google Cast is unavailable", e)
        null
    }

    var onMediaLoaded: (() -> Unit)? = null
    var onLoadFailed: ((statusCode: Int) -> Unit)? = null
    var onConnectFailed: ((deviceName: String?) -> Unit)? = null

    private var pendingVideo: DetectedVideo? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = loadPending(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = loadPending(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            pendingVideo = null
            onConnectFailed?.invoke(session.castDevice?.friendlyName)
        }
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    val isAvailable: Boolean
        get() = castContext != null

    val isConnected: Boolean
        get() = castContext?.sessionManager?.currentCastSession?.isConnected == true

    val routeSelector: MediaRouteSelector?
        get() = castContext?.mergedSelector

    fun register() {
        castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
    }

    fun unregister() {
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
    }

    /** Remembers [video] so it starts as soon as the session being set up connects. */
    fun loadWhenConnected(video: DetectedVideo) {
        pendingVideo = video
    }

    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    /** Returns false if there is no connected session to load into. */
    fun load(video: DetectedVideo): Boolean {
        val session = castContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected } ?: return false
        val client = session.remoteMediaClient ?: return false

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, video.displayName)
            putString(MediaMetadata.KEY_SUBTITLE, video.host)
        }
        val mediaInfo = MediaInfo.Builder(video.url)
            .setContentUrl(video.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(video.mimeType)
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()
        client.load(request).setResultCallback { result ->
            if (result.status.isSuccess) onMediaLoaded?.invoke() else onLoadFailed?.invoke(result.status.statusCode)
        }
        return true
    }

    private fun loadPending(session: CastSession) {
        val video = pendingVideo ?: return
        pendingVideo = null
        if (session.isConnected) load(video)
    }

    private companion object {
        const val TAG = "ChromecastController"
    }
}
