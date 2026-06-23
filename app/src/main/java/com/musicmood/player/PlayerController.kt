package com.musicmood.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.musicmood.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton che incapsula MediaController e lo espone come Flow per la UI.
 */
class PlayerController private constructor(context: Context) {

    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    init {
        val token = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, PlaybackService::class.java)
        )
        MediaController.Builder(context.applicationContext, token).buildAsync()
            .let { future ->
                future.addListener({
                    controller = future.get()
                    attachListener()
                    syncState()
                }, MoreExecutors.directExecutor())
            }
    }

    private fun attachListener() {
        controller?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) = syncState()
            override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()
            override fun onPlaybackStateChanged(playbackState: Int) = syncState()
        })
    }

    private fun syncState() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _state.value = PlaybackUiState(
            isPlaying    = c.isPlaying,
            title        = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist       = item?.mediaMetadata?.artist?.toString().orEmpty(),
            artworkUri   = item?.mediaMetadata?.artworkUri,
            hasItem      = item != null,
        )
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        val c = controller ?: return
        val items = queue.map { it.toMediaItem() }
        val startIndex = queue.indexOf(song).coerceAtLeast(0)
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }
    fun prev() { controller?.seekToPrevious() }

    fun release() {
        controller?.release()
        controller = null
    }

    private fun Song.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .build()
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    data class PlaybackUiState(
        val isPlaying: Boolean = false,
        val title: String = "",
        val artist: String = "",
        val artworkUri: Uri? = null,
        val hasItem: Boolean = false,
    )

    companion object {
        @Volatile private var INSTANCE: PlayerController? = null
        fun get(context: Context): PlayerController =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayerController(context).also { INSTANCE = it }
            }
    }
}
