package com.musicmood.export

import android.content.Context
import android.net.Uri
import com.musicmood.data.MoodRepository
import com.musicmood.library.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter

/**
 * Esporta la libreria analizzata in CSV (UTF-8, separatore virgola, virgolette doppie).
 * Formato compatibile con Excel / Power BI / Google Sheets.
 */
class CsvExporter(private val context: Context) {

    private val moodRepo = MoodRepository.get(context)
    private val mediaRepo = MediaStoreRepository(context)

    /** Esporta verso un Uri scelto dall'utente tramite ACTION_CREATE_DOCUMENT. */
    suspend fun exportTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val songs = mediaRepo.loadAllSongs().associateBy { it.id }
            val analyses = mutableListOf<Pair<Long, com.musicmood.data.db.MoodEntity>>()

            // raccogliamo tutte le righe Room una volta
            moodRepo.observeAll().collect { entities ->
                analyses.clear()
                entities.forEach { analyses += it.songId to it }
                return@collect
            }
            // collect non termina mai: usiamo first()
        }.recover { _ ->
            // fallback: usiamo una via diretta
            val dao = com.musicmood.data.db.AppDatabase.get(context).moodDao()
            val songs = mediaRepo.loadAllSongs().associateBy { it.id }
            val analyses = dao.getAnalyzedIds().mapNotNull { id ->
                dao.findById(id)
            }
            writeCsv(uri, songs, analyses)
            analyses.size
        }
    }

    private fun writeCsv(
        uri: Uri,
        songs: Map<Long, com.musicmood.data.Song>,
        analyses: List<com.musicmood.data.db.MoodEntity>,
    ) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8)).use { writer ->
                // Header
                writer.write(buildHeader())
                writer.newLine()

                analyses.forEach { e ->
                    val song = songs[e.songId]
                    writer.write(buildRow(song, e))
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

    private fun buildRow(
        song: com.musicmood.data.Song?,
        e: com.musicmood.data.db.MoodEntity,
    ): String {
        val title  = csvEscape(song?.title ?: "")
        val artist = csvEscape(song?.artist ?: "")
        val album  = csvEscape(song?.album ?: "")
        val duration = song?.durationMs ?: 0L
        return listOf(
            e.songId.toString(),
            title, artist, album,
            duration.toString(),
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
    }

    /** Gestisce virgole, virgolette e newline incapsulando con virgolette doppie. */
    private fun csvEscape(s: String): String {
        if (s.isEmpty()) return ""
        val needsEscape = s.contains(",") || s.contains("\"") ||
                          s.contains("\n") || s.contains("\r")
        if (!needsEscape) return s
        val escaped = s.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
