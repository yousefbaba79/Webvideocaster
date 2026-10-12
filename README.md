# WebVideoCaster

An Android app in the spirit of *Web Video Cast*: browse any website, let the app find the videos on the page, and send them to your TV.

## Features

- **Built-in browser**: address bar with search, back/forward, fullscreen web video, and "Open with" / "Share to" support for links.
- **Video detection**:
  - watches every network request the page makes, which catches MP4/WebM/MKV/MOV files and HLS (`.m3u8`) / DASH (`.mpd`) playlists, even inside iframes;
  - injects a small script that reports `<video>` / `<source>` elements, including ones added later.
  - A floating **"N videos"** button opens the list. Tap a video to cast it; long-press to cast to another device, play it on the phone, or copy the link.
- **Chromecast / Google TV**: uses the Google Cast SDK with the Default Media Receiver, a mini controller, an expanded controller and a media notification.
- **DLNA / UPnP smart TVs** (Samsung, LG, Sony, Philips, Kodi, …): finds TVs with SSDP and controls them over UPnP AVTransport (play, pause, stop, seek, live position).
- **Cast a link…**: paste any direct video URL.

## Using it

1. Open a site and start playing its video in the page. The **videos** button appears once the app finds a stream.
2. Tap the button and pick a video.
3. Pick a device. The TV button lists Chromecasts and DLNA TVs together; the Cast button is the standard Chromecast picker. Later videos go to the same device until you disconnect.
4. **⋮ → Now playing** reopens the remote.

The phone and TV must be on the same Wi-Fi network.

## Building

Requirements: JDK 17+ and the Android SDK (API 35). Android Studio Ladybug or newer works out of the box.

```bash
./gradlew testDebugUnitTest   # unit tests (URL detection, SSDP, UPnP parsing, SOAP client)
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
```

Every push runs the **Android build** GitHub Actions workflow. Download the debug APK from the run's *Artifacts* section to install it on a phone.

## Project layout

```
app/src/main/java/com/webvideocaster/app/
├── MainActivity.kt            browser screen, menus, intents
├── browser/
│   ├── VideoDetector.kt       network + DOM video detection, injected JS
│   ├── MediaUrlClassifier.kt  "is this URL a video, and what type?"
│   └── UrlInput.kt            address bar → URL or search
├── cast/
│   ├── Caster.kt              routes a video to the connected / chosen device
│   ├── DevicePickerDialog.kt  unified Chromecast + DLNA picker
│   ├── ChromecastController.kt, CastOptionsProvider.kt, ExpandedControlsActivity.kt
└── dlna/
    ├── SsdpClient.kt          M-SEARCH discovery
    ├── DeviceDescriptionParser.kt
    ├── AvTransportClient.kt   SOAP: SetAVTransportURI, Play, Pause, Seek, …
    └── DlnaControlActivity.kt DLNA remote control screen
```

## Known limitations

- The TV fetches the video URL itself, so streams that need the site's cookies, a `Referer` header or DRM (Netflix, Disney+, etc.) won't play. Pages that only expose `blob:` URLs work only if their underlying HLS/DASH playlist is visible on the network.
- Many DLNA TVs can't play HLS/DASH; MP4 works best on them.
- Without Google Play services, Chromecast is unavailable and only DLNA is offered.

## Roadmap ideas

Roku and Fire TV support, a local proxy that adds headers for protected streams, subtitles, casting local files, bookmarks/history, and an ad blocker.
