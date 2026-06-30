package com.musicmood.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.musicmood.R
import com.musicmood.data.ArtworkRepository
import com.musicmood.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SongAdapter(
    private val onClick: (Song) -> Unit,
    private val onLongClick: (Song) -> Unit,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val artworkRepo: ArtworkRepository,
) : ListAdapter<Song, SongAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.artJob?.cancel()
        holder.artJob = null
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val art      = view.findViewById<ImageView>(R.id.albumArt)
        private val title    = view.findViewById<TextView>(R.id.title)
        private val artist   = view.findViewById<TextView>(R.id.artist)
        private val duration = view.findViewById<TextView>(R.id.duration)
        private val mood     = view.findViewById<TextView>(R.id.mood)

        var artJob: Job? = null

        fun bind(s: Song) {
            title.text    = s.title
            artist.text   = s.artist.ifBlank { s.album }
            duration.text = s.durationFormatted

            if (s.mood != null) {
                mood.text = "🎯 ${s.mood}"
                mood.visibility = View.VISIBLE
            } else {
                mood.visibility = View.GONE
            }

            // 1. Carica subito MediaStore artwork (se c'è)
            Glide.with(art)
                .load(s.albumArtUri)
                .placeholder(R.drawable.ic_music_placeholder)
                .error(R.drawable.ic_music_placeholder)
                .centerCrop()
                .into(art)

            // 2. In parallelo prova a recuperare una versione remota (cache aware)
            artJob?.cancel()
            artJob = lifecycleScope.launch {
                val remoteUrl = withContext(Dispatchers.IO) {
                    runCatching {
                        artworkRepo.getOrFetch(s.id, s.title, s.artist)
                    }.getOrNull()
                }
                if (remoteUrl != null) {
                    Glide.with(art)
                        .load(remoteUrl)
                        .placeholder(R.drawable.ic_music_placeholder)
                        .error(R.drawable.ic_music_placeholder)
                        .centerCrop()
                        .into(art)
                }
            }

            itemView.setOnClickListener { onClick(s) }
            itemView.setOnLongClickListener {
                onLongClick(s)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(a: Song, b: Song) = a.id == b.id
            override fun areContentsTheSame(a: Song, b: Song) = a == b
        }
    }
}

// Extension property se non l'hai già nel modello Song
private val Song.durationFormatted: String
    get() {
        val sec = durationMs / 1000
        val m = sec / 60
        val s = sec % 60
        return "%d:%02d".format(m, s)
    }
