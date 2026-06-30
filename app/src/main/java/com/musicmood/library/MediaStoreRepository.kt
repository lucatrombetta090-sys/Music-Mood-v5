package com.musicmood.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.musicmood.data.Song
import java.io.File

class MediaStoreRepository(private val context: Context) {

    fun loadAllSongs(): List<Song> {
        val songs = mutableListOf<Song>()

        // Mappa songId -> genre name (popolata da una query separata)
        val genreById = loadGenresMap()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val idIdx       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIdx    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIdx   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdx    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIdx  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durIdx      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val yearIdx     = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val dataIdx     = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val mimeIdx     = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val title = cursor.getString(titleIdx) ?: continue
                val artist = cursor.getString(artistIdx) ?: ""
                val album  = cursor.getString(albumIdx) ?: ""
                val albumId = cursor.getLong(albumIdIdx)
                val duration = cursor.getLong(durIdx)
                val yearVal = if (yearIdx >= 0) cursor.getInt(yearIdx) else 0
                val data = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "audio/*" else "audio/*"

                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                val albumArt = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )

                val folder = data?.let { File(it).parentFile?.name }

                songs += Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration,
                    uri = uri,
                    albumArtUri = albumArt,
                    mimeType = mime,
                    genre = genreById[id],
                    year = yearVal.takeIf { it > 0 },
                    folderPath = folder,
                )
            }
        }
        return songs
    }

    private fun loadGenresMap(): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        runCatching {
            val genresUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            val genresProj = arrayOf(
                MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME,
            )
            context.contentResolver.query(genresUri, genresProj, null, null, null)?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (c.moveToNext()) {
                    val genreId = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: continue
                    if (name.isBlank()) continue

                    val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    val mProj = arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID)
                    context.contentResolver.query(membersUri, mProj, null, null, null)?.use { m ->
                        val audioIdx = m.getColumnIndex(MediaStore.Audio.Genres.Members.AUDIO_ID)
                        if (audioIdx >= 0) {
                            while (m.moveToNext()) {
                                map[m.getLong(audioIdx)] = name
                            }
                        }
                    }
                }
            }
        }
        return map
    }
}
