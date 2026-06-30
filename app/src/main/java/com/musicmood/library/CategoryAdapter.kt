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

class CategoryAdapter(
    private val onClick: (CategoryGroup) -> Unit,
) : ListAdapter<CategoryGroup, CategoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val art      = view.findViewById<ImageView>(R.id.catCover)
        private val title    = view.findViewById<TextView>(R.id.catTitle)
        private val subtitle = view.findViewById<TextView>(R.id.catSubtitle)

        fun bind(g: CategoryGroup) {
            title.text    = g.title
            subtitle.text = g.subtitle

            val coverUri = g.coverSong?.albumArtUri
            if (coverUri != null) {
                Glide.with(art)
                    .load(coverUri)
                    .placeholder(R.drawable.ic_music_placeholder)
                    .error(R.drawable.ic_music_placeholder)
                    .centerCrop()
                    .into(art)
            } else {
                Glide.with(art).clear(art)
                art.setImageResource(R.drawable.ic_music_placeholder)
            }

            itemView.setOnClickListener { onClick(g) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CategoryGroup>() {
            override fun areItemsTheSame(a: CategoryGroup, b: CategoryGroup) = a.key == b.key
            override fun areContentsTheSame(a: CategoryGroup, b: CategoryGroup) = a == b
        }
    }
}
