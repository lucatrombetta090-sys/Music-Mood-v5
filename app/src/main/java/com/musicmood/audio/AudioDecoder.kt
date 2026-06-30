package com.musicmood.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioDecoder(private val context: Context) {

    private val tag = "AudioDecoder"

    data class PcmBuffer(
        val bytes: ByteArray,
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Long,
    )

    fun decodeWindow(uri: Uri, startMs: Long, durationMs: Long): PcmBuffer {
        val safeDuration = durationMs.coerceAtMost(30_000L)
        val maxOutputBytes = 10 * 1024 * 1024

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
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
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
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

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

        val output = ByteArrayOutputStream(2 * 1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L
        val endTimeUs = (startMs + safeDuration) * 1_000L
        var inputDone = false
        var outputDone = false
        var loopGuard = 0
        val maxLoops = 10_000

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
                        output.size() >= maxOutputBytes
                    ) {
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
        output.reset()
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

    /**
     * Restituisce PCM float32 MONO a 16 kHz, normalizzato in [-1, +1].
     * Formato richiesto da YAMNet.
     */
    fun decodeFloat16k(uri: Uri, startMs: Long, durationMs: Long): FloatArray {
        val raw = decodeWindow(uri, startMs, durationMs)
        return convertToFloat16kMono(raw)
    }

    private fun convertToFloat16kMono(pcm: PcmBuffer): FloatArray {
        val bb = ByteBuffer.wrap(pcm.bytes).order(ByteOrder.LITTLE_ENDIAN)
        val totalSamples = pcm.bytes.size / 2
        val asFloat = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            val s = bb.short.toInt()
            asFloat[i] = s / 32768f
        }

        val mono = if (pcm.channels == 1) {
            asFloat
        } else {
            val out = FloatArray(asFloat.size / pcm.channels)
            for (i in out.indices) {
                var sum = 0f
                for (c in 0 until pcm.channels) {
                    sum += asFloat[i * pcm.channels + c]
                }
                out[i] = sum / pcm.channels
            }
            out
        }

        val targetSr = 16_000
        if (pcm.sampleRate == targetSr) return mono
        val newLen = (mono.size.toLong() * targetSr / pcm.sampleRate).toInt()
        val resampled = FloatArray(newLen)
        for (i in 0 until newLen) {
            val srcPos = i.toDouble() * pcm.sampleRate / targetSr
            val idx = srcPos.toInt()
            val frac = (srcPos - idx).toFloat()
            val a = mono[idx.coerceIn(0, mono.size - 1)]
            val b = mono[(idx + 1).coerceIn(0, mono.size - 1)]
            resampled[i] = a + (b - a) * frac
        }
        return resampled
    }
}
