package com.webvideocaster.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.media.widget.MiniControllerFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webvideocaster.app.browser.DetectedVideo
import com.webvideocaster.app.browser.MediaUrlClassifier
import com.webvideocaster.app.browser.UrlInput
import com.webvideocaster.app.browser.VideoDetector
import com.webvideocaster.app.cast.Caster
import com.webvideocaster.app.databinding.ActivityMainBinding
import com.webvideocaster.app.ui.VideoListSheet

/** The browser: load any site, collect the videos it plays, and cast them. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var caster: Caster
    private lateinit var detector: VideoDetector
    private lateinit var videoSheet: VideoListSheet

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                customView != null -> exitFullscreen()
                binding.webView.canGoBack() -> binding.webView.goBack()
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        caster = Caster(this)
        caster.register()
        if (caster.chromecast.isAvailable && savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.miniControllerContainer, MiniControllerFragment())
                .commit()
        }

        detector = VideoDetector(::onVideosChanged)
        videoSheet = VideoListSheet(this, onCast = caster::cast, onMore = ::showVideoOptions)
        setupWebView()
        setupUrlBar()
        binding.videosFab.setOnClickListener { videoSheet.show(detector.currentVideos) }
        onBackPressedDispatcher.addCallback(this, backCallback)

        val restored = savedInstanceState != null && binding.webView.restoreState(savedInstanceState) != null
        if (!restored && !handleIntent(intent)) binding.webView.loadUrl(UrlInput.HOME_URL)
        requestNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        backCallback.isEnabled = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onDestroy() {
        caster.unregister()
        binding.webView.destroy()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        if (caster.chromecast.isAvailable) {
            CastButtonFactory.setUpMediaRouteButton(applicationContext, menu, R.id.media_route_menu_item)
        } else {
            menu.findItem(R.id.media_route_menu_item).isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_devices -> caster.chooseDeviceAndCast(null)
            R.id.action_now_playing -> caster.openNowPlaying()
            R.id.action_cast_link -> promptForLink()
            R.id.action_reload -> binding.webView.reload()
            R.id.action_forward -> if (binding.webView.canGoForward()) binding.webView.goForward()
            R.id.action_home -> binding.webView.loadUrl(UrlInput.HOME_URL)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() = with(binding.webView) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        addJavascriptInterface(detector.bridge, VideoDetector.JS_BRIDGE_NAME)
        webViewClient = BrowserClient()
        webChromeClient = ChromeClient()
        setDownloadListener { url, _, _, mimeType, _ ->
            val mime = MediaUrlClassifier.mimeFromContentType(mimeType) ?: MediaUrlClassifier.classify(url)
            if (mime != null) {
                detector.add(DetectedVideo(url, mime, null, binding.webView.url))
                videoSheet.show(detector.currentVideos)
            }
        }
    }

    private fun setupUrlBar() {
        binding.urlBar.setOnEditorActionListener { view, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || isEnter) {
                openUrlOrVideo(UrlInput.toUrl(view.text.toString()))
                view.clearFocus()
                getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    private fun handleIntent(intent: Intent?): Boolean {
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::extractUrl)
            else -> null
        } ?: return false
        openUrlOrVideo(url)
        return true
    }

    private fun extractUrl(text: String): String? {
        val matcher = Patterns.WEB_URL.matcher(text)
        if (!matcher.find()) return null
        val url = matcher.group()
        return if (url.contains("://")) url else "https://$url"
    }

    /** Direct video links go straight to the cast flow; anything else opens in the browser. */
    private fun openUrlOrVideo(url: String) {
        val mime = MediaUrlClassifier.classify(url)
        if (mime == null) {
            binding.webView.loadUrl(url)
            return
        }
        if (binding.webView.url == null) binding.webView.loadUrl(UrlInput.HOME_URL)
        val video = DetectedVideo(url, mime, null, null)
        detector.add(video)
        caster.cast(video)
    }

    private fun onVideosChanged(videos: List<DetectedVideo>) {
        binding.videosFab.text = resources.getQuantityString(R.plurals.videos_found, videos.size, videos.size)
        if (videos.isEmpty()) binding.videosFab.hide() else binding.videosFab.show()
        videoSheet.update(videos)
    }

    private fun showVideoOptions(video: DetectedVideo) {
        val options = arrayOf(
            getString(R.string.video_action_cast_to),
            getString(R.string.video_action_play_local),
            getString(R.string.video_action_copy),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(video.displayName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> caster.chooseDeviceAndCast(video)
                    1 -> playLocally(video)
                    2 -> copyLink(video)
                }
            }
            .show()
    }

    private fun playLocally(video: DetectedVideo) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(video.url), video.mimeType))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_player_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLink(video: DetectedVideo) {
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(video.displayName, video.url))
        Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    private fun promptForLink() {
        val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding)
        val input = EditText(this).apply {
            hint = getString(R.string.cast_link_hint)
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        val container = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cast_link_title)
            .setView(container)
            .setPositiveButton(R.string.cast) { _, _ ->
                val url = UrlInput.toUrl(input.text.toString())
                if (!MediaUrlClassifier.isCastableScheme(url)) return@setPositiveButton
                val mime = MediaUrlClassifier.classify(url) ?: MediaUrlClassifier.MP4
                val video = DetectedVideo(url, mime, null, null)
                detector.add(video)
                caster.cast(video)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateUrlBar(url: String?) {
        if (!binding.urlBar.hasFocus() && url != null) binding.urlBar.setText(url)
    }

    private fun openExternally(uri: Uri) {
        try {
            val intent = if (uri.scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    // Never let a web page target a specific (possibly non-exported) component.
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
            } else {
                Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            }
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                intent.getStringExtra("browser_fallback_url")
                    ?.takeIf { MediaUrlClassifier.isCastableScheme(it) }
                    ?.let(binding.webView::loadUrl)
            }
        } catch (e: Exception) {
            // Malformed intent: URI; ignore like a browser would.
        }
    }

    private fun exitFullscreen() {
        val view = customView ?: return
        binding.fullscreenContainer.removeView(view)
        binding.fullscreenContainer.isVisible = false
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    private inner class BrowserClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return false
            openExternally(request.url)
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            updateUrlBar(url)
            detector.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            updateUrlBar(url)
            view.evaluateJavascript(VideoDetector.INJECTED_SCRIPT, null)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            updateUrlBar(url)
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            detector.onRequest(request.url.toString())
            return null
        }
    }

    private inner class ChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            binding.progress.isVisible = newProgress < 100
            binding.progress.setProgressCompat(newProgress, true)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            detector.onTitle(title)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) {
                callback.onCustomViewHidden()
                return
            }
            customView = view
            customViewCallback = callback
            binding.fullscreenContainer.addView(
                view,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            binding.fullscreenContainer.isVisible = true
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        override fun onHideCustomView() {
            exitFullscreen()
        }
    }
}
