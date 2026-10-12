package com.webvideocaster.app.cast

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.mediarouter.media.MediaRouter
import com.webvideocaster.app.R
import com.webvideocaster.app.browser.DetectedVideo
import com.webvideocaster.app.browser.MediaUrlClassifier
import com.webvideocaster.app.dlna.AvTransportClient
import com.webvideocaster.app.dlna.DlnaControlActivity
import com.webvideocaster.app.dlna.DlnaDevice
import com.webvideocaster.app.dlna.DlnaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sends videos to whichever device is connected, asking the user to pick one when none is. */
class Caster(private val activity: AppCompatActivity) {
    val chromecast = ChromecastController(activity).apply {
        onMediaLoaded = { activity.startActivity(Intent(activity, ExpandedControlsActivity::class.java)) }
        onLoadFailed = { code -> toast(activity.getString(R.string.chromecast_load_failed, code)) }
        onConnectFailed = { name -> toast(activity.getString(R.string.chromecast_connect_failed, name ?: "Chromecast")) }
    }

    fun register() = chromecast.register()

    fun unregister() = chromecast.unregister()

    fun cast(video: DetectedVideo) {
        val dlna = DlnaState.activeDevice
        when {
            chromecast.isConnected -> chromecast.load(video)
            dlna != null -> castToDlna(dlna, video)
            else -> chooseDeviceAndCast(video)
        }
    }

    /** Shows the device picker; with a null [video] it only connects to the chosen device. */
    fun chooseDeviceAndCast(video: DetectedVideo?) {
        DevicePickerDialog(activity, chromecast) { target -> castTo(target, video) }.show()
    }

    fun openNowPlaying() {
        val dlna = DlnaState.activeDevice
        when {
            chromecast.isConnected -> activity.startActivity(Intent(activity, ExpandedControlsActivity::class.java))
            dlna != null -> activity.startActivity(DlnaControlActivity.intent(activity, dlna, DlnaState.nowPlayingTitle))
            else -> toast(activity.getString(R.string.nothing_playing))
        }
    }

    private fun castTo(target: CastTarget, video: DetectedVideo?) {
        when (target) {
            is CastTarget.Chromecast -> {
                DlnaState.activeDevice = null
                if (target.route.isSelected && chromecast.isConnected) {
                    video?.let(chromecast::load)
                } else {
                    video?.let(chromecast::loadWhenConnected)
                    toast(activity.getString(R.string.connecting_to, target.name))
                    MediaRouter.getInstance(activity).selectRoute(target.route)
                }
            }
            is CastTarget.Dlna -> {
                if (chromecast.isConnected) chromecast.disconnect()
                DlnaState.activeDevice = target.device
                if (video != null) {
                    castToDlna(target.device, video)
                } else {
                    toast(activity.getString(R.string.connected_to, target.name))
                }
            }
        }
    }

    private fun castToDlna(device: DlnaDevice, video: DetectedVideo) {
        toast(activity.getString(R.string.sending_to, device.friendlyName))
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    AvTransportClient(device.avTransportControlUrl, device.avTransportServiceType)
                        .load(video.url, video.displayName, dlnaMimeType(video.mimeType))
                }
            }
            result.onSuccess {
                DlnaState.nowPlayingTitle = video.displayName
                activity.startActivity(DlnaControlActivity.intent(activity, device, video.displayName))
            }.onFailure { error ->
                toast(activity.getString(R.string.cast_failed, device.friendlyName, error.message ?: error.toString()))
            }
        }
    }

    /** TVs expect the registered HLS type rather than the x- variant the Cast receiver uses. */
    private fun dlnaMimeType(mimeType: String): String =
        if (mimeType == MediaUrlClassifier.HLS) "application/vnd.apple.mpegurl" else mimeType

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
