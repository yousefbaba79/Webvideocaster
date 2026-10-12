package com.webvideocaster.app.cast

import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webvideocaster.app.R
import com.webvideocaster.app.dlna.DlnaDevice
import com.webvideocaster.app.dlna.DlnaDiscovery
import com.webvideocaster.app.dlna.DlnaState
import kotlinx.coroutines.launch

/** Lists Chromecasts and DLNA TVs as they are discovered and reports the one the user taps. */
class DevicePickerDialog(
    private val activity: AppCompatActivity,
    private val chromecast: ChromecastController,
    private val onPicked: (CastTarget) -> Unit,
) {
    private val targets = mutableListOf<CastTarget>()
    private val adapter = object : ArrayAdapter<CastTarget>(activity, R.layout.item_device, R.id.deviceName, targets) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val target = targets[position]
            view.findViewById<TextView>(R.id.deviceName).text = target.name
            view.findViewById<TextView>(R.id.deviceDescription).text = target.description
            view.findViewById<ImageView>(R.id.deviceIcon).setImageResource(
                if (target is CastTarget.Chromecast) R.drawable.ic_cast else R.drawable.ic_tv,
            )
            return view
        }
    }

    fun show() {
        DlnaState.knownDevices().forEach(::addDlna)

        val router = MediaRouter.getInstance(activity)
        val selector = chromecast.routeSelector
        val routerCallback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshChromecasts(router, selector)
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshChromecasts(router, selector)
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshChromecasts(router, selector)
        }
        if (selector != null) {
            router.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            refreshChromecasts(router, selector)
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.searching_devices)
            .setAdapter(adapter) { _, which -> targets.getOrNull(which)?.let(onPicked) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        val discovery = activity.lifecycleScope.launch {
            DlnaDiscovery(activity).discover { addDlna(it) }
            dialog.setTitle(if (targets.isEmpty()) R.string.no_devices_found else R.string.choose_device)
        }
        dialog.setOnDismissListener {
            router.removeCallback(routerCallback)
            discovery.cancel()
        }
        dialog.show()
    }

    private fun refreshChromecasts(router: MediaRouter, selector: MediaRouteSelector?) {
        if (selector == null) return
        val routes = router.routes.filter {
            !it.isDefault && !it.isBluetooth && it.isEnabled && it.matchesSelector(selector)
        }
        targets.removeAll { it is CastTarget.Chromecast }
        targets.addAll(0, routes.map { CastTarget.Chromecast(it) })
        adapter.notifyDataSetChanged()
    }

    private fun addDlna(device: DlnaDevice) {
        val index = targets.indexOfFirst { it is CastTarget.Dlna && it.device.id == device.id }
        if (index >= 0) targets[index] = CastTarget.Dlna(device) else targets.add(CastTarget.Dlna(device))
        adapter.notifyDataSetChanged()
    }
}
