package com.webvideocaster.app.dlna

/** A UPnP/DLNA media renderer (smart TV, speaker, etc.) that exposes an AVTransport service. */
data class DlnaDevice(
    val id: String,
    val friendlyName: String,
    val location: String,
    val avTransportControlUrl: String,
    val avTransportServiceType: String,
    val manufacturer: String? = null,
    val modelName: String? = null,
)
