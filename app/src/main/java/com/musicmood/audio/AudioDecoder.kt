package com.musicmood.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream

class AudioDecoder(private val context: Context) {

    private val tag = "AudioDecoder"

    data class PcmBuffer(
        val bytes: ByteArray,
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Long,
    )

    /**
     * Decodifica una finestra audio in PCM 16-bit little-endian.
     * Hard limits per evitare OOM:
     *   - max 30 secondi
     *   - max 10 MB di output
     */
    fun decodeWindow(uri: Uri, startMs: Long, durationMs: Long): PcmBuffer {
        val safeDuration = durationMs.coerceAtMost(30_000L)
        val maxOutputBytes = 10 * 1024 * 1024  // 10 MB

        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: error("Impossibile aprire $uri")
        } catch (e: Exception) {
            extractor.release()
            throw IllegalStateException("Apertura fallita: ${e.message}", e)
        }

        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            throw IllegalStateException("Nessuna traccia audio")
        }

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: run { extractor.release(); throw IllegalStateException("MIME nullo") }
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels   = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        extractor.seekTo(startMs * 1_000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val codec = try {
            MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
        } catch (e: Exception) {
            extractor.release()
            throw IllegalStateException("Codec $mime non supportato: ${e.message}", e)
        }

        val output = ByteArrayOutputStream(2 * 1024 * 1024)  // 2 MB iniziali
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L
        val endTimeUs = (startMs + safeDuration) * 1_000L
        var inputDone = false
        var outputDone = false
        var loopGuard = 0
        val maxLoops = 10_000  // protezione anti-infinite-loop

        try {
            while (!outputDone && loopGuard++ < maxLoops) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0 || extractor.sampleTime > endTimeUs) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inIdx, 0, size,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    if (bufferInfo.size > 0 && output.size() < maxOutputBytes) {
                        val chunk = ByteArray(bufferInfo.size)
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        outBuf.get(chunk)
                        output.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 ||
                        output.size() >= maxOutputBytes) {
                        outputDone = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Decode error: ${e.message}")
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }

        val pcm = output.toByteArray()
        output.reset()  // libera memoria del ByteArrayOutputStream

        if (pcm.isEmpty()) {
            throw IllegalStateException("PCM vuoto dopo decode")
        }

        return PcmBuffer(
            bytes = pcm,
            sampleRate = sampleRate,
            channels = channels,
            durationMs = (pcm.size / 2L / channels) * 1000L / sampleRate,
        )
    }
}
