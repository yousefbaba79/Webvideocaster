package com.webvideocaster.app.cast

import androidx.mediarouter.media.MediaRouter
import com.webvideocaster.app.dlna.DlnaDevice

/** A device the user can cast to. */
sealed class CastTarget {
    abstract val name: String
    abstract val description: String

    class Chromecast(val route: MediaRouter.RouteInfo) : CastTarget() {
        override val name: String get() = route.name
        override val description: String get() = "Chromecast"
    }

    class Dlna(val device: DlnaDevice) : CastTarget() {
        override val name: String get() = device.friendlyName
        override val description: String
            get() = listOfNotNull("DLNA", device.manufacturer, device.modelName)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
    }

    override fun toString(): String = name
}
