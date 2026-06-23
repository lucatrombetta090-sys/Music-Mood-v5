package com.musicmood.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.musicmood.R
import com.musicmood.data.Song

class SongAdapter(
    private val onClick: (Song) -> Unit,
    private val onLongClick: (Song) -> Unit,
) : ListAdapter<Song, SongAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val art      = view.findViewById<ImageView>(R.id.albumArt)
        private val title    = view.findViewById<TextView>(R.id.title)
        private val artist   = view.findViewById<TextView>(R.id.artist)
        private val duration = view.findViewById<TextView>(R.id.duration)
        private val mood     = view.findViewById<TextView>(R.id.mood)

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

            Glide.with(art)
                .load(s.albumArtUri)
                .placeholder(R.drawable.ic_music_placeholder)
                .error(R.drawable.ic_music_placeholder)
                .centerCrop()
                .into(art)

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
