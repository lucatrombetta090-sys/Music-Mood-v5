package com.musicmood.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder hardware basato su MediaExtractor + MediaCodec.
 * Estrae PCM 16-bit LE da qualsiasi formato supportato (MP3, M4A, FLAC, OGG…)
 * a partire da [startMs] per una durata massima di [durationMs].
 *
 * NOTA: il risultato è mono se possibile; altrimenti restituiamo i canali originali
 * e lascia al lato Python il downmix.
 */
class AudioDecoder(private val context: Context) {

    data class PcmBuffer(
        val bytes: ByteArray,
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Long,
    )

    fun decodeWindow(uri: Uri, startMs: Long, durationMs: Long): PcmBuffer {
        val extractor = MediaExtractor().apply {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                setDataSource(pfd.fileDescriptor)
            } ?: error("Impossibile aprire URI: $uri")
        }

        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            error("Nessuna traccia audio trovata")
        }

        extractor.selectTrack(trackIndex)
        val format     = extractor.getTrackFormat(trackIndex)
        val mime       = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels   = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        // Posiziona il cursore vicino a startMs
        extractor.seekTo(startMs * 1_000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val codec = MediaCodec.createDecoderByType(mime).apply {
            configure(format, null, null, 0)
            start()
        }

        val output = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L
        val endTimeUs = (startMs + durationMs) * 1_000L
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0 || extractor.sampleTime > endTimeUs) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inIdx, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    if (bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        outBuf.get(chunk)
                        output.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }

        val pcm = output.toByteArray()
        // Forza byte order little-endian (formato atteso da numpy.int16)
        // MediaCodec su Android restituisce già LE su tutte le architetture target.
        return PcmBuffer(
            bytes = pcm,
            sampleRate = sampleRate,
            channels = channels,
            durationMs = (pcm.size / 2L / channels) * 1000L / sampleRate,
        )
    }
}
