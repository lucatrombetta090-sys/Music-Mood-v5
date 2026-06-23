package com.musicmood.export

import android.content.Context
import android.net.Uri
import com.musicmood.data.db.AppDatabase
import com.musicmood.data.db.MoodEntity
import com.musicmood.library.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter

class CsvExporter(private val context: Context) {

    private val dao = AppDatabase.get(context).moodDao()
    private val mediaRepo = MediaStoreRepository(context)

    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = mediaRepo.loadAllSongs().associateBy { it.id }
            val analyzedIds = dao.getAnalyzedIds()
            val analyses = analyzedIds.mapNotNull { dao.findById(it) }
            writeCsv(uri, songs, analyses)
            analyses.size
        }
    }

    private fun writeCsv(
        uri: Uri,
        songs: Map<Long, com.musicmood.data.Song>,
        analyses: List<MoodEntity>,
    ) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8)).use { writer ->
                writer.write(buildHeader())
                writer.newLine()
                analyses.forEach { e ->
                    writer.write(buildRow(songs[e.songId], e))
                    writer.newLine()
                }
            }
        } ?: error("Impossibile aprire output stream per $uri")
    }

    private fun buildHeader(): String = listOf(
        "song_id", "title", "artist", "album", "duration_ms",
        "mood", "confidence", "valence", "arousal",
        "tempo_bpm", "key", "mode",
        "analyzed_at", "analyzer_version",
    ).joinToString(",")

    private fun buildRow(song: com.musicmood.data.Song?, e: MoodEntity): String =
        listOf(
            e.songId.toString(),
            csvEscape(song?.title ?: ""),
            csvEscape(song?.artist ?: ""),
            csvEscape(song?.album ?: ""),
            (song?.durationMs ?: 0L).toString(),
            csvEscape(e.mood),
            "%.3f".format(e.confidence),
            "%.3f".format(e.valence),
            "%.3f".format(e.arousal),
            "%.1f".format(e.tempoBpm),
            csvEscape(e.musicKey),
            csvEscape(e.mode),
            e.analyzedAt.toString(),
            csvEscape(e.analyzerVersion),
        ).joinToString(",")

    private fun csvEscape(s: String): String {
        if (s.isEmpty()) return ""
        val needsEscape = s.contains(",") || s.contains("\"") ||
                          s.contains("\n") || s.contains("\r")
        if (!needsEscape) return s
        return "\"${s.replace("\"", "\"\"")}\""
    }
}
