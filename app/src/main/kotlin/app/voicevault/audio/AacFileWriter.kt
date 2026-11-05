package app.voicevault.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Scrive la registrazione in AAC-LC mono dentro un .m4a.
 *
 * Nota rispetto a docs/DESIGN.md, che diceva Opus 48 kbps: l'encoder Opus di
 * Android non e' garantito su tutti i dispositivi, mentre AAC-LC c'e' sempre.
 * A 64 kbps mono con il microfono a 3 cm dalla bocca la voce e' ottima e resta
 * rianalizzabile. Costo: ~14 MB al giorno, ~5 GB all'anno.
 */
class AacFileWriter(
    private val sampleRate: Int,
    private val bitRate: Int = 64_000,
) {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var totalSamples = 0L
    private val bufferInfo = MediaCodec.BufferInfo()

    fun start(file: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /** Chiamata dal thread di cattura, con i campioni appena letti. */
    fun write(pcm: ShortArray, count: Int) {
        val c = codec ?: return
        var offset = 0
        while (offset < count) {
            val inIndex = c.dequeueInputBuffer(10_000)
            if (inIndex < 0) { drain(false); continue }
            val input = c.getInputBuffer(inIndex) ?: continue
            input.clear()
            val capacitySamples = input.capacity() / 2
            val chunk = minOf(capacitySamples, count - offset)
            val shorts = input.order(ByteOrder.nativeOrder()).asShortBuffer()
            shorts.put(pcm, offset, chunk)
            val ptsUs = totalSamples * 1_000_000L / sampleRate
            c.queueInputBuffer(inIndex, 0, chunk * 2, ptsUs, 0)
            totalSamples += chunk
            offset += chunk
            drain(false)
        }
    }

    fun stop() {
        val c = codec ?: return
        runCatching {
            val inIndex = c.dequeueInputBuffer(100_000)
            if (inIndex >= 0) {
                val ptsUs = totalSamples * 1_000_000L / sampleRate
                c.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drain(true)
        }
        runCatching { c.stop() }
        c.release()
        codec = null
        muxer?.let { m ->
            if (muxerStarted) runCatching { m.stop() }
            runCatching { m.release() }
        }
        muxer = null
        muxerStarted = false
        trackIndex = -1
        totalSamples = 0
    }

    private fun drain(endOfStream: Boolean) {
        val c = codec ?: return
        val m = muxer ?: return
        while (true) {
            val index = c.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = m.addTrack(c.outputFormat)
                        m.start()
                        muxerStarted = true
                    }
                }
                index >= 0 -> {
                    val out: ByteBuffer? = c.getOutputBuffer(index)
                    if (out != null && bufferInfo.size > 0 && muxerStarted &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        out.position(bufferInfo.offset)
                        out.limit(bufferInfo.offset + bufferInfo.size)
                        m.writeSampleData(trackIndex, out, bufferInfo)
                    }
                    c.releaseOutputBuffer(index, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
            if (!endOfStream && index < 0) return
        }
    }
}
