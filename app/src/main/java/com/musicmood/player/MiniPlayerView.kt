package com.musicmood.player

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.musicmood.R
import kotlinx.coroutines.launch

@UnstableApi
class MiniPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val tag = "MiniPlayerView"

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

        // Click sui pulsanti
        playPause.setOnClickListener {
            Log.d(tag, "play/pause clicked")
            controller.toggle()
        }
        next.setOnClickListener {
            Log.d(tag, "next clicked")
            controller.next()
        }
        prev.setOnClickListener {
            Log.d(tag, "prev clicked")
            controller.prev()
        }

        // Tap su qualsiasi area NON-pulsante → apri PlayerActivity
        val openPlayer = View.OnClickListener {
            Log.d(tag, "tap → opening PlayerActivity")
            openPlayerActivity()
        }
        art.setOnClickListener(openPlayer)
        title.setOnClickListener(openPlayer)
        artist.setOnClickListener(openPlayer)
        // Anche click sull'area vuota della card
        setOnClickListener(openPlayer)

        visibility = View.GONE
    }

    private fun openPlayerActivity() {
        try {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e(tag, "openPlayerActivity failed: ${t.message}", t)
        }
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
