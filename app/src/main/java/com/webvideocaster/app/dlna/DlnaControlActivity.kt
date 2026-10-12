package com.webvideocaster.app.dlna

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.webvideocaster.app.R
import com.webvideocaster.app.databinding.ActivityDlnaControlBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Remote control for a video playing on a DLNA TV. */
class DlnaControlActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDlnaControlBinding
    private lateinit var client: AvTransportClient
    private var positionSeconds = 0L
    private var isPlaying = false
    private var userSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityDlnaControlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val controlUrl = intent.getStringExtra(EXTRA_CONTROL_URL)
        if (controlUrl == null) {
            finish()
            return
        }
        client = AvTransportClient(
            controlUrl,
            intent.getStringExtra(EXTRA_SERVICE_TYPE) ?: AvTransportClient.DEFAULT_SERVICE_TYPE,
        )

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.playing_on, intent.getStringExtra(EXTRA_DEVICE_NAME).orEmpty())
        binding.videoTitle.text = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        binding.playPause.setOnClickListener { command { if (isPlaying) client.pause() else client.play() } }
        binding.rewind.setOnClickListener { command { client.seek((positionSeconds - 10).coerceAtLeast(0)) } }
        binding.fastForward.setOnClickListener { command { client.seek(positionSeconds + 30) } }
        binding.stop.setOnClickListener { command { client.stop() } }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.position.text = formatClock(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                userSeeking = false
                val target = seekBar.progress.toLong()
                command { client.seek(target) }
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refresh()
                    delay(1000)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dlna_control, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_disconnect -> {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { runCatching { client.stop() } }
                DlnaState.activeDevice = null
                DlnaState.nowPlayingTitle = null
                finish()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun command(action: () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { runCatching(action) }.exceptionOrNull()?.let { error ->
                Toast.makeText(this@DlnaControlActivity, error.message ?: error.toString(), Toast.LENGTH_LONG).show()
            }
            refresh()
        }
    }

    private suspend fun refresh() {
        val result = withContext(Dispatchers.IO) {
            runCatching { client.getPositionInfo() to client.getTransportState() }
        }
        val (info, state) = result.getOrNull() ?: run {
            binding.state.setText(R.string.state_unreachable)
            return
        }
        positionSeconds = info.positionSeconds ?: 0
        val duration = info.durationSeconds ?: 0
        isPlaying = state == "PLAYING" || state == "TRANSITIONING"

        binding.state.setText(
            when (state) {
                "PLAYING" -> R.string.state_playing
                "PAUSED_PLAYBACK" -> R.string.state_paused
                "TRANSITIONING" -> R.string.state_loading
                "STOPPED", "NO_MEDIA_PRESENT" -> R.string.state_stopped
                else -> R.string.state_unknown
            },
        )
        binding.playPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        binding.playPause.contentDescription = getString(if (isPlaying) R.string.pause else R.string.play)
        binding.duration.text = formatClock(duration)
        if (!userSeeking) {
            binding.seekBar.max = duration.toInt().coerceAtLeast(0)
            binding.seekBar.progress = positionSeconds.toInt()
            binding.position.text = formatClock(positionSeconds)
        }
    }

    private fun formatClock(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    companion object {
        private const val EXTRA_CONTROL_URL = "control_url"
        private const val EXTRA_SERVICE_TYPE = "service_type"
        private const val EXTRA_DEVICE_NAME = "device_name"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, device: DlnaDevice, title: String?): Intent =
            Intent(context, DlnaControlActivity::class.java)
                .putExtra(EXTRA_CONTROL_URL, device.avTransportControlUrl)
                .putExtra(EXTRA_SERVICE_TYPE, device.avTransportServiceType)
                .putExtra(EXTRA_DEVICE_NAME, device.friendlyName)
                .putExtra(EXTRA_TITLE, title)
    }
}
