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
import com.musicmood.data.ArtworkRepository
import com.musicmood.data.MoodRepository
import com.musicmood.library.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val artworkRepo = ArtworkRepository.get(context)
    private val mediaRepo = MediaStoreRepository(context)

    private var artJob: Job? = null
    private var lastSongId: Long = -1L

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

        val openPlayer = View.OnClickListener { openPlayerActivity() }
        art.setOnClickListener(openPlayer)
        title.setOnClickListener(openPlayer)
        artist.setOnClickListener(openPlayer)
        setOnClickListener(openPlayer)

        visibility = View.GONE
    }

    private fun openPlayerActivity() {
        try {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            Log.e(tag, "openPlayerActivity failed: ${t.message}")
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

                // Prima carica MediaStore per feedback immediato
                Glide.with(art)
                    .load(s.artworkUri)
                    .placeholder(R.drawable.ic_music_placeholder)
                    .into(art)

                // Poi prova iTunes/Deezer cache-aware
                val songId = s.currentSongId ?: -1L
                if (songId > 0 && songId != lastSongId) {
                    lastSongId = songId
                    artJob?.cancel()
                    artJob = owner.lifecycleScope.launch {
                        val remoteUrl = withContext(Dispatchers.IO) {
                            runCatching {
                                artworkRepo.getOrFetch(songId, s.title, s.artist)
                            }.getOrNull()
                        }
                        if (remoteUrl != null) {
                            Glide.with(art)
                                .load(remoteUrl)
                                .placeholder(R.drawable.ic_music_placeholder)
                                .into(art)
                        }
                    }
                }
            }
        }
    }
}
