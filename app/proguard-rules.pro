# Keep the JavaScript bridge methods that the injected page script calls.
-keepclassmembers class com.webvideocaster.app.browser.VideoDetector$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
