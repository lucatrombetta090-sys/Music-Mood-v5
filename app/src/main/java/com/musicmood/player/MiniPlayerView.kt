package com.musicmood.player

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.musicmood.R
import kotlinx.coroutines.launch

/**
 * Mini-player persistente in fondo a MainActivity.
 * Si auto-osserva PlayerController.state e si nasconde se non c'è nulla.
 */
class MiniPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val art: ShapeableImageView
    private val title: TextView
    private val artist: TextView
    private val playPause: ImageButton
    private val next: ImageButton
    private val prev: ImageButton

    private val controller = PlayerController.get(context)

    init {
        LayoutInflater.from(context).inflate(R.layout.view_mini_player, this, true)
        art       = findViewById(R.id.mpArt)
        title     = findViewById(R.id.mpTitle)
        artist    = findViewById(R.id.mpArtist)
        playPause = findViewById(R.id.mpPlayPause)
        next      = findViewById(R.id.mpNext)
        prev      = findViewById(R.id.mpPrev)

        playPause.setOnClickListener { controller.toggle() }
        next.setOnClickListener { controller.next() }
        prev.setOnClickListener { controller.prev() }
        visibility = View.GONE
    }

    fun observe(owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            controller.state.collect { s ->
                if (!s.hasItem) {
                    visibility = View.GONE
                    return@collect
                }
                visibility = View.VISIBLE
                title.text  = s.title.ifBlank { context.getString(R.string.unknown_title) }
                artist.text = s.artist.ifBlank { context.getString(R.string.unknown_artist) }
                playPause.setImageResource(
                    if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
                Glide.with(art)
                    .load(s.artworkUri)
                    .placeholder(R.drawable.ic_music_placeholder)
                    .into(art)
            }
        }
    }
}
