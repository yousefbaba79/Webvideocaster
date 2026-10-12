package com.webvideocaster.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.webvideocaster.app.browser.DetectedVideo
import com.webvideocaster.app.databinding.ItemVideoBinding
import com.webvideocaster.app.databinding.SheetVideosBinding

/** Bottom sheet listing the videos found on the current page. Tap casts; long-press shows more options. */
class VideoListSheet(
    context: Context,
    private val onCast: (DetectedVideo) -> Unit,
    private val onMore: (DetectedVideo) -> Unit,
) {
    private val dialog = BottomSheetDialog(context)
    private val binding = SheetVideosBinding.inflate(LayoutInflater.from(context))
    private val adapter = VideoAdapter()

    init {
        binding.videoList.layoutManager = LinearLayoutManager(context)
        binding.videoList.adapter = adapter
        dialog.setContentView(binding.root)
    }

    fun show(videos: List<DetectedVideo>) {
        update(videos)
        dialog.show()
    }

    fun update(videos: List<DetectedVideo>) {
        adapter.submit(videos)
        binding.emptyText.isVisible = videos.isEmpty()
        binding.videoList.isVisible = videos.isNotEmpty()
    }

    private inner class VideoAdapter : RecyclerView.Adapter<VideoAdapter.Holder>() {
        private var items: List<DetectedVideo> = emptyList()

        fun submit(videos: List<DetectedVideo>) {
            items = videos
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val video = items[position]
            holder.binding.videoTitle.text = video.displayName
            holder.binding.videoFormat.text = video.formatLabel
            holder.binding.videoDetails.text = listOf(video.host, video.fileName)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
            holder.binding.root.setOnClickListener {
                dialog.dismiss()
                onCast(video)
            }
            holder.binding.root.setOnLongClickListener {
                onMore(video)
                true
            }
        }

        inner class Holder(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
