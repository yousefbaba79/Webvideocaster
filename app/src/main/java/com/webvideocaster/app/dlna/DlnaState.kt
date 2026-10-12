package com.webvideocaster.app.dlna

/** In-memory DLNA state shared across screens: devices seen so far and the one we are casting to. */
object DlnaState {
    private val known = LinkedHashMap<String, DlnaDevice>()

    @Volatile
    var activeDevice: DlnaDevice? = null

    @Volatile
    var nowPlayingTitle: String? = null

    @Synchronized
    fun remember(device: DlnaDevice) {
        known[device.id] = device
    }

    @Synchronized
    fun knownDevices(): List<DlnaDevice> = known.values.toList()
}
