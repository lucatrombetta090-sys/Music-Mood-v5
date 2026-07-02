package com.musicmood.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.musicmood.data.Song
import com.musicmood.data.db.AppDatabase
import com.musicmood.data.db.ListeningEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@UnstableApi
class PlayerController private constructor(private val context: Context) {

    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val songMoodCache = mutableMapOf<Long, String>()

    init {
        val token = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, PlaybackService::class.java)
        )

        val future = MediaController.Builder(
            context.applicationContext,
            token
        ).buildAsync()

        future.addListener({
            controller = future.get()
            attachListener()
            syncState()
        }, MoreExecutors.directExecutor())
    }

    private fun attachListener() {
        val listener = object : Player.Listener {

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncState()
                mediaItem?.let { logListeningEvent(it) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncState()
            }
        }

        controller?.addListener(listener)
    }

    private fun syncState() {
        val c = controller ?: return
        val item = c.currentMediaItem

        _state.value = PlaybackUiState(
            isPlaying = c.isPlaying,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            artworkUri = item?.mediaMetadata?.artworkUri,
            hasItem = item != null,
            currentSongId = item?.mediaId?.toLongOrNull()
        )
    }

    private fun logListeningEvent(item: MediaItem) {
        val songId = item.mediaId.toLongOrNull() ?: return
        val mood = songMoodCache[songId] ?: return

        scope.launch {
            AppDatabase.get(context).listeningEventDao().insert(
                ListeningEventEntity(
                    songId = songId,
                    mood = mood
                )
            )
        }
    }

    fun playSong(
        song: Song,
        queue: List<Song> = listOf(song)
    ) {
        val c = controller ?: return

        queue.forEach { queuedSong ->
            queuedSong.effectiveMood?.let { mood ->
                songMoodCache[queuedSong.id] = mood
            }
        }

        val items = queue.map { it.toMediaItem() }

        val startIndex = queue
            .indexOfFirst { it.id == song.id }
            .coerceAtLeast(0)

        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()

        song.effectiveMood?.let { mood ->
            scope.launch {
                AppDatabase.get(context).listeningEventDao().insert(
                    ListeningEventEntity(
                        songId = song.id,
                        mood = mood
                    )
                )
            }
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun prev() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun getProgress(): PlayerProgress {
        val c = controller ?: return PlayerProgress(0L, 0L)

        return PlayerProgress(
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L)
        )
    }

    fun currentSongId(): Long? {
        return controller?.currentMediaItem?.mediaId?.toLongOrNull()
    }

    fun release() {
        controller?.release()
        controller = null
    }

    private fun Song.toMediaItem(): MediaItem {
        val resolvedArtworkUri: Uri? = artworkUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }
            ?: albumArtUri

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(resolvedArtworkUri)
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
        val currentSongId: Long? = null
    )

    companion object {
        @Volatile
        private var INSTANCE: PlayerController? = null

        fun get(context: Context): PlayerController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayerController(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
