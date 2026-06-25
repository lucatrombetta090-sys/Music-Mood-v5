package com.musicmood.player

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.musicmood.R
import kotlinx.coroutines.launch

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private val vm: PlayerViewModel by androidx.activity.viewModels()

    private lateinit var artwork: ShapeableImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var moodChip: Chip
    private lateinit var seekBar: Slider
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var btnPlayPause: FloatingActionButton

    private var isSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_MusicMood_Player)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_player)

        artwork      = findViewById(R.id.artworkBig)
        title        = findViewById(R.id.songTitle)
        artist       = findViewById(R.id.songArtist)
        moodChip     = findViewById(R.id.moodChip)
        seekBar      = findViewById(R.id.seekBar)
        currentTime  = findViewById(R.id.currentTime)
        totalTime    = findViewById(R.id.totalTime)
        btnPlayPause = findViewById(R.id.btnPlayPauseBig)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { vm.prev() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { vm.next() }
        btnPlayPause.setOnClickListener { vm.toggle() }

        findViewById<ImageButton>(R.id.btnPlayerMenu).setOnClickListener { v ->
            PopupMenu(this, v).apply {
                menuInflater.inflate(R.menu.player_menu, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_share -> {
                            Snackbar.make(v, R.string.player_share_coming,
                                Snackbar.LENGTH_SHORT).show()
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        seekBar.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) { isSeeking = true }
            override fun onStopTrackingTouch(slider: Slider) {
                isSeeking = false
                vm.seekToMs(slider.value.toLong())
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.playerState.collect { s ->
                        if (!s.hasItem) {
                            finish(); return@collect
                        }
                        title.text = s.title
                        artist.text = s.artist
                        btnPlayPause.setImageResource(
                            if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        )
                        Glide.with(artwork)
                            .load(s.artworkUri)
                            .placeholder(R.drawable.ic_music_placeholder)
                            .into(artwork)
                    }
                }
                launch {
                    vm.progress.collect { p ->
                        if (p.durationMs > 0) {
                            seekBar.valueTo = p.durationMs.toFloat()
                            if (!isSeeking) {
                                seekBar.value = p.positionMs.toFloat()
                                    .coerceIn(0f, seekBar.valueTo)
                            }
                            currentTime.text = formatMs(p.positionMs)
                            totalTime.text   = formatMs(p.durationMs)
                        }
                    }
                }
                launch {
                    vm.currentMood.collect { mood ->
                        if (mood != null) {
                            moodChip.text = "🎯 $mood"
                            moodChip.visibility = View.VISIBLE
                        } else {
                            moodChip.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun formatMs(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(sec / 60, sec % 60)
    }
}
