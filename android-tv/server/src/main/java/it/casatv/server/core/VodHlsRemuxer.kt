package it.casatv.server.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Real-time container remuxer (MKV / AVI / MOV -> HLS MPEG-TS) backed by Android's
 * MediaExtractor bitstream copy (zero video/audio re-encoding) so Safari and other browsers
 * can play MKV movies/series natively with full scrubbing.
 */
class VodHlsRemuxer {

    companion object {
        private const val TAG = "CasaVodHls"
        private const val SEG_DURATION_SEC = 6
        private const val SEG_DURATION_US = SEG_DURATION_SEC * 1_000_000L
        private const val MAX_SESSIONS = 3
        private const val SESSION_IDLE_TTL_MS = 20 * 60_000L
        private const val MAX_CACHED_SEGMENTS = 6
        private const val MAX_READ_BUF_BYTES = 8 * 1024 * 1024
        private const val STREAM_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

        private const val PID_PAT = 0x0000
        private const val PID_PMT = 0x1000
        private const val PID_VIDEO = 0x0100
        private const val PID_AUDIO = 0x0101

        private val H264_AUD = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x09, 0xF0.toByte())
        private val HEVC_AUD = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x46, 0x01, 0x50)
        private val SAMPLE_RATES = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000, 7350,
        )
        private val MPEG_CRC32_TABLE = IntArray(256).apply {
            for (i in 0 until 256) {
                var crc = i shl 24
                for (bit in 0 until 8) {
                    crc = if ((crc and 0x80000000.toInt()) != 0) {
                        (crc shl 1) xor 0x04C11DB7
                    } else {
                        crc shl 1
                    }
                }
                this[i] = crc
            }
        }
    }

    private class CachedSample(
        val trackIndex: Int,
        val timeUs: Long,
        val flags: Int,
        val data: ByteArray,
    )

    private class RemuxSession(
        val id: String,
        val url: String,
        val extractor: MediaExtractor,
        val videoTrackIdx: Int,
        val audioTrackIdx: Int,
        val videoStreamType: Int,
        val audioStreamType: Int,
        val isHevc: Boolean,
        val videoParamSetsAnnexB: ByteArray,
        val videoNalLengthSize: Int,
        val isAacAdts: Boolean,
        val aacProfileMinusOne: Int,
        val aacFreqIdx: Int,
        val aacChanCfg: Int,
        val durationUs: Long,
        val frameStep90k: Long,
    ) {
        val lock = Any()
        @Volatile
        var closed: Boolean = false
        var lastAccessMs: Long = System.currentTimeMillis()
        var nextExpectedSeg: Int = 0
        var pendingSample: CachedSample? = null
        var lastVideoDts90k: Long = -1L
        var lastSegEndedOnKeyframe: Boolean = true
        var readBuf: ByteBuffer = ByteBuffer.allocateDirect(1024 * 1024)
        var ccPat = 0
        var ccPmt = 0
        var ccVideo = 0
        var ccAudio = 0
        val segCache = LinkedHashMap<Int, ByteArray>(MAX_CACHED_SEGMENTS + 2, 0.75f, true)

        fun putCached(segIdx: Int, bytes: ByteArray) {
            segCache[segIdx] = bytes
            while (segCache.size > MAX_CACHED_SEGMENTS) {
                val eldest = segCache.keys.firstOrNull() ?: break
                segCache.remove(eldest)
            }
        }

        fun close() {
            synchronized(lock) {
                if (closed) return
                closed = true
                runCatching { extractor.release() }
                segCache.clear()
                pendingSample = null
            }
        }
    }

    private val sessions = ConcurrentHashMap<String, RemuxSession>()
    private val openLock = Any()

    fun getActiveSessionUrl(id: String): String? =
        sessions[id]?.takeIf { !it.closed }?.url

    fun getPlaylist(id: String, streamUrl: String): String? {
        val session = getOrOpenSession(id, streamUrl) ?: return null
        session.lastAccessMs = System.currentTimeMillis()
        val totalSec = (session.durationUs / 1_000_000.0).coerceAtLeast(SEG_DURATION_SEC.toDouble())
        val segCount = kotlin.math.ceil(totalSec / SEG_DURATION_SEC.toDouble()).toInt().coerceAtLeast(1)
        val sb = StringBuilder(segCount * 36 + 160)
        sb.append("#EXTM3U\n")
        sb.append("#EXT-X-VERSION:3\n")
        sb.append("#EXT-X-TARGETDURATION:").append(SEG_DURATION_SEC + 6).append('\n')
        sb.append("#EXT-X-MEDIA-SEQUENCE:0\n")
        sb.append("#EXT-X-PLAYLIST-TYPE:VOD\n")
        for (i in 0 until segCount) {
            val startSec = i * SEG_DURATION_SEC.toDouble()
            val segDur = if (i == segCount - 1) {
                (totalSec - startSec).coerceIn(0.5, SEG_DURATION_SEC.toDouble() + 2.0)
            } else {
                SEG_DURATION_SEC.toDouble()
            }
            sb.append("#EXTINF:").append(String.format(Locale.US, "%.3f", segDur)).append(",\n")
            sb.append("seg_").append(i).append(".ts\n")
        }
        sb.append("#EXT-X-ENDLIST\n")
        return sb.toString()
    }

    fun getSegment(id: String, streamUrl: String, segIndex: Int): ByteArray? {
        if (segIndex < 0) return null
        val session = getOrOpenSession(id, streamUrl) ?: return null
        synchronized(session.lock) {
            if (session.closed) return null
            session.lastAccessMs = System.currentTimeMillis()
            session.segCache[segIndex]?.let { return it }

            val totalSegs = kotlin.math.ceil(
                (session.durationUs / 1_000_000.0).coerceAtLeast(SEG_DURATION_SEC.toDouble()) / SEG_DURATION_SEC,
            ).toInt().coerceAtLeast(1)
            if (segIndex >= totalSegs) return null

            val startUs = segIndex.toLong() * SEG_DURATION_US
            val endUs = (segIndex + 1).toLong() * SEG_DURATION_US
            val isLastSeg = segIndex == totalSegs - 1
            val isSequentialCont = segIndex == session.nextExpectedSeg && session.pendingSample != null && !session.lastSegEndedOnKeyframe

            if (segIndex != session.nextExpectedSeg) {
                session.pendingSample = null
                session.lastVideoDts90k = ((startUs.coerceAtLeast(0L) * 90L) / 1000L) + 117_000L
                runCatching {
                    session.extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                }.onFailure {
                    Log.w(TAG, "Seek failed for $id seg=$segIndex: ${it.message}")
                    return null
                }
            }

            val tsOut = ByteArrayOutputStream(512 * 1024)
            writePatPacket(tsOut, session)
            writePmtPacket(tsOut, session)

            var seenVideoKeyframe = isSequentialCont
            var pcrEmitted = false
            var sampleCount = 0
            var audioFrames = 0
            var videoFrames = 0
            var endedOnKeyframeSplit = true
            val maxSamples = if (isLastSeg) 25_000 else 6_000

            fun takeNextSample(): CachedSample? {
                val pending = session.pendingSample
                if (pending != null) {
                    session.pendingSample = null
                    return pending
                }
                val trackIdx = session.extractor.sampleTrackIndex
                if (trackIdx < 0) return null
                var size = -1
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val needed = session.extractor.sampleSize.toInt()
                    if (needed > session.readBuf.capacity() && needed <= MAX_READ_BUF_BYTES) {
                        session.readBuf = ByteBuffer.allocateDirect(needed.coerceAtLeast(session.readBuf.capacity() * 2))
                    }
                }
                while (true) {
                    session.readBuf.clear()
                    try {
                        size = session.extractor.readSampleData(session.readBuf, 0)
                        break
                    } catch (e: IllegalArgumentException) {
                        val nextCap = session.readBuf.capacity() * 2
                        if (nextCap > MAX_READ_BUF_BYTES) {
                            Log.w(TAG, "Sample exceeds $MAX_READ_BUF_BYTES bytes in $id")
                            return null
                        }
                        session.readBuf = ByteBuffer.allocateDirect(nextCap)
                    }
                }
                if (size < 0) return null
                val timeUs = session.extractor.sampleTime
                val flags = session.extractor.sampleFlags
                val arr = ByteArray(size)
                session.readBuf.position(0)
                session.readBuf.get(arr, 0, size)
                session.extractor.advance()
                return CachedSample(trackIdx, timeUs, flags, arr)
            }

            while (sampleCount < maxSamples) {
                val sample = takeNextSample() ?: break
                val isVideo = sample.trackIndex == session.videoTrackIdx
                val isAudio = sample.trackIndex == session.audioTrackIdx
                val isKeyframe = isVideo && (sample.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                // Split at keyframe boundary past endUs (with +3s safety cap) so next segment starts on a clean IDR.
                if (!isLastSeg && isVideo && sample.timeUs >= endUs && (isKeyframe || sample.timeUs >= endUs + 3_000_000L)) {
                    if (videoFrames > 0) {
                        endedOnKeyframeSplit = isKeyframe
                        session.pendingSample = sample
                        break
                    }
                }
                if (!isLastSeg && sample.timeUs >= endUs + 3_500_000L && (videoFrames > 0 || audioFrames > 0)) {
                    endedOnKeyframeSplit = false
                    session.pendingSample = sample
                    break
                }

                // Wait until we hit a video keyframe when starting a video-bearing segment.
                if (session.videoTrackIdx >= 0 && !seenVideoKeyframe) {
                    if (!isVideo || !isKeyframe) {
                        if (videoFrames == 0 && sample.timeUs < startUs - 500_000L) continue
                        if (isAudio && session.videoTrackIdx >= 0) continue
                        if (isVideo && !isKeyframe) continue
                    }
                }

                var pts90k = ((sample.timeUs.coerceAtLeast(0L) * 90L) / 1000L) + 135_000L
                if (isVideo) {
                    if (isKeyframe) seenVideoKeyframe = true
                    val dts90k = if (session.lastVideoDts90k < 0L) {
                        (pts90k - 18_000L).coerceAtLeast(90_000L)
                    } else {
                        minOf(pts90k, session.lastVideoDts90k + session.frameStep90k)
                            .coerceAtLeast(session.lastVideoDts90k + 90L)
                    }
                    if (pts90k < dts90k) pts90k = dts90k
                    session.lastVideoDts90k = dts90k

                    val nalPayload = convertVideoSampleToAnnexB(
                        data = sample.data,
                        nalLengthSize = session.videoNalLengthSize,
                        isHevc = session.isHevc,
                        isKeyframe = isKeyframe,
                        paramSetsAnnexB = session.videoParamSetsAnnexB,
                    )
                    val pes = buildPesPacket(
                        streamId = 0xE0,
                        pts90k = pts90k,
                        dts90k = if (pts90k != dts90k) dts90k else null,
                        payload = nalPayload,
                    )
                    val emitPcrNow = isKeyframe || !pcrEmitted || (videoFrames % 3 == 0)
                    val pcrVal = if (emitPcrNow) (dts90k - 9_000L).coerceAtLeast(0L) else null
                    if (pcrVal != null) pcrEmitted = true
                    writePesAsTsPackets(
                        out = tsOut,
                        pid = PID_VIDEO,
                        pes = pes,
                        randomAccess = isKeyframe,
                        pcr90k = pcrVal,
                        nextCc = {
                            val c = session.ccVideo
                            session.ccVideo = (c + 1) and 0x0F
                            c
                        },
                    )
                    videoFrames++
                } else if (isAudio) {
                    val audioPayload = if (session.isAacAdts) {
                        ensureAdtsFrame(
                            data = sample.data,
                            profileMinusOne = session.aacProfileMinusOne,
                            freqIdx = session.aacFreqIdx,
                            chanCfg = session.aacChanCfg,
                        )
                    } else {
                        sample.data
                    }
                    val streamId = if (session.audioStreamType == 0x81 || session.audioStreamType == 0x87) 0xBD else 0xC0
                    val pes = buildPesPacket(streamId = streamId, pts90k = pts90k, dts90k = null, payload = audioPayload)
                    writePesAsTsPackets(
                        out = tsOut,
                        pid = PID_AUDIO,
                        pes = pes,
                        randomAccess = true,
                        pcr90k = if (session.videoTrackIdx < 0 && (!pcrEmitted || audioFrames % 4 == 0)) {
                            pcrEmitted = true
                            (pts90k - 9_000L).coerceAtLeast(0L)
                        } else {
                            null
                        },
                        nextCc = {
                            val c = session.ccAudio
                            session.ccAudio = (c + 1) and 0x0F
                            c
                        },
                    )
                    audioFrames++
                }
                sampleCount++
            }

            session.lastSegEndedOnKeyframe = endedOnKeyframeSplit
            session.nextExpectedSeg = segIndex + 1
            val result = tsOut.toByteArray()
            if (result.size > 376) {
                session.putCached(segIndex, result)
            }
            return result
        }
    }

    fun shutdown() {
        synchronized(openLock) {
            sessions.values.forEach { it.close() }
            sessions.clear()
        }
    }

    private fun getOrOpenSession(id: String, streamUrl: String): RemuxSession? {
        pruneIdleSessions()
        val existing = sessions[id]
        if (existing != null && !existing.closed && existing.url == streamUrl) {
            return existing
        }

        // Open MediaExtractor outside openLock so slow VOD servers don't stall other sessions.
        val extractor = MediaExtractor()
        val opened = runCatching {
            extractor.setDataSource(
                streamUrl,
                mapOf(
                    "User-Agent" to STREAM_USER_AGENT,
                    "Accept" to "*/*",
                ),
            )
            true
        }.getOrElse { err ->
            Log.w(TAG, "MediaExtractor failed to open $id ($streamUrl): ${err.message}")
            runCatching { extractor.release() }
            false
        }
        if (!opened) return null

        var vIdx = -1
        var aIdx = -1
        var vStreamType = 0x1B
        var aStreamType = 0x0F
        var isHevc = false
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME)?.lowercase(Locale.ROOT).orEmpty()
            if (vIdx < 0 && (mime == "video/avc" || mime == "video/hevc")) {
                vIdx = i
                videoFormat = fmt
                isHevc = mime == "video/hevc"
                vStreamType = if (isHevc) 0x24 else 0x1B
            } else if (aIdx < 0 && mime.startsWith("audio/")) {
                val st = when (mime) {
                    "audio/mp4a-latm" -> 0x0F
                    "audio/ac3" -> 0x81
                    "audio/eac3" -> 0x87
                    "audio/mpeg", "audio/mpeg-l2" -> 0x03
                    else -> -1
                }
                if (st > 0) {
                    aIdx = i
                    audioFormat = fmt
                    aStreamType = st
                }
            }
        }

        if (vIdx < 0 && aIdx < 0) {
            Log.w(TAG, "No H.264/HEVC/AAC/AC3 track found in $id (tracks=${extractor.trackCount})")
            runCatching { extractor.release() }
            return null
        }
        if (vIdx >= 0) extractor.selectTrack(vIdx)
        if (aIdx >= 0) extractor.selectTrack(aIdx)

        val (paramSets, nalLenSize) = if (videoFormat != null) {
            extractVideoParameterSets(videoFormat, isHevc)
        } else {
            ByteArray(0) to 4
        }

        val fps = videoFormat?.takeIf { it.containsKey(MediaFormat.KEY_FRAME_RATE) }
            ?.let { fmt ->
                runCatching { fmt.getInteger(MediaFormat.KEY_FRAME_RATE) }
                    .getOrElse { runCatching { fmt.getFloat(MediaFormat.KEY_FRAME_RATE).toInt() }.getOrDefault(0) }
            } ?: 0
        val frameStep90k = if (fps in 10..120) (90_000L / fps).coerceIn(750L, 9000L) else 3750L

        var isAac = aStreamType == 0x0F
        var aacProfMinusOne = 1 // AAC-LC
        var aacFreqIdx = 3 // 48000Hz
        var aacChanCfg = 2
        if (isAac && audioFormat != null) {
            val csd = audioFormat.getByteBuffer("csd-0")
            if (csd != null && csd.remaining() >= 2) {
                val dup = csd.duplicate()
                val b0 = dup.get().toInt() and 0xFF
                val b1 = dup.get().toInt() and 0xFF
                val objType = (b0 ushr 3) and 0x1F
                val rawFreqIdx = ((b0 and 0x07) shl 1) or ((b1 ushr 7) and 0x01)
                val rawChan = (b1 ushr 3) and 0x0F
                aacProfMinusOne = when {
                    objType in 1..4 -> objType - 1
                    else -> 1 // AAC-LC fallback for SBR/PS explicit signaling
                }
                aacFreqIdx = if (rawFreqIdx in 0..12) rawFreqIdx else freqIdxFromHz(
                    if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 48000,
                )
                aacChanCfg = if (rawChan in 1..7) rawChan else (
                    if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
                ).coerceIn(1, 7)
            } else {
                val sr = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 48000
                val ch = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
                aacFreqIdx = freqIdxFromHz(sr)
                aacChanCfg = ch.coerceIn(1, 7)
            }
        } else {
            isAac = false
        }

        val durUs = listOfNotNull(
            videoFormat?.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }?.getLong(MediaFormat.KEY_DURATION),
            audioFormat?.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }?.getLong(MediaFormat.KEY_DURATION),
        ).maxOrNull()?.takeIf { it > 0L } ?: (7200L * 1_000_000L)

        synchronized(openLock) {
            val doubleCheck = sessions[id]
            if (doubleCheck != null && !doubleCheck.closed && doubleCheck.url == streamUrl) {
                runCatching { extractor.release() }
                return doubleCheck
            }
            sessions.remove(id)?.close()
            while (sessions.size >= MAX_SESSIONS) {
                val oldest = sessions.entries.minByOrNull { it.value.lastAccessMs }?.key ?: break
                sessions.remove(oldest)?.close()
            }

            val session = RemuxSession(
                id = id,
                url = streamUrl,
                extractor = extractor,
                videoTrackIdx = vIdx,
                audioTrackIdx = aIdx,
                videoStreamType = vStreamType,
                audioStreamType = aStreamType,
                isHevc = isHevc,
                videoParamSetsAnnexB = paramSets,
                videoNalLengthSize = nalLenSize,
                isAacAdts = isAac,
                aacProfileMinusOne = aacProfMinusOne,
                aacFreqIdx = aacFreqIdx,
                aacChanCfg = aacChanCfg,
                durationUs = durUs,
                frameStep90k = frameStep90k,
            )
            sessions[id] = session
            Log.i(TAG, "Opened VOD HLS remux session id=$id dur=${durUs / 1_000_000L}s vType=$vStreamType aType=$aStreamType step=$frameStep90k")
            return session
        }
    }

    private fun pruneIdleSessions() {
        val now = System.currentTimeMillis()
        val toClose = ArrayList<RemuxSession>()
        sessions.entries.removeIf { (_, s) ->
            val expired = now - s.lastAccessMs > SESSION_IDLE_TTL_MS
            if (expired) toClose.add(s)
            expired
        }
        toClose.forEach { it.close() }
    }

    private fun freqIdxFromHz(hz: Int): Int {
        val idx = SAMPLE_RATES.indexOf(hz)
        return if (idx >= 0) idx else 3
    }

    private fun extractVideoParameterSets(fmt: MediaFormat, isHevc: Boolean): Pair<ByteArray, Int> {
        val out = ByteArrayOutputStream(128)
        var nalLengthSize = 4
        val csd0 = fmt.getByteBuffer("csd-0")?.let { bb ->
            ByteArray(bb.remaining()).also { bb.duplicate().get(it) }
        }
        val csd1 = fmt.getByteBuffer("csd-1")?.let { bb ->
            ByteArray(bb.remaining()).also { bb.duplicate().get(it) }
        }

        if (csd0 != null && csd0.isNotEmpty()) {
            val startsWithStartCode = (csd0.size >= 4 && csd0[0] == 0.toByte() && csd0[1] == 0.toByte() && csd0[2] == 0.toByte() && csd0[3] == 1.toByte()) ||
                (csd0.size >= 3 && csd0[0] == 0.toByte() && csd0[1] == 0.toByte() && csd0[2] == 1.toByte())
            if (startsWithStartCode) {
                out.write(csd0)
                if (csd1 != null && csd1.isNotEmpty()) out.write(csd1)
            } else if (!isHevc && csd0.size >= 7 && csd0[0] == 1.toByte()) {
                // AVCDecoderConfigurationRecord
                nalLengthSize = (csd0[4].toInt() and 0x03) + 1
                var pos = 5
                val numSps = csd0[pos++].toInt() and 0x1F
                repeat(numSps) {
                    if (pos + 2 > csd0.size) return@repeat
                    val len = ((csd0[pos].toInt() and 0xFF) shl 8) or (csd0[pos + 1].toInt() and 0xFF)
                    pos += 2
                    if (pos + len <= csd0.size && len > 0) {
                        out.write(byteArrayOf(0, 0, 0, 1))
                        out.write(csd0, pos, len)
                    }
                    pos += len
                }
                if (pos < csd0.size) {
                    val numPps = csd0[pos++].toInt() and 0xFF
                    repeat(numPps) {
                        if (pos + 2 > csd0.size) return@repeat
                        val len = ((csd0[pos].toInt() and 0xFF) shl 8) or (csd0[pos + 1].toInt() and 0xFF)
                        pos += 2
                        if (pos + len <= csd0.size && len > 0) {
                            out.write(byteArrayOf(0, 0, 0, 1))
                            out.write(csd0, pos, len)
                        }
                        pos += len
                    }
                }
            } else if (isHevc && csd0.size >= 23 && csd0[0] == 1.toByte()) {
                // HEVCDecoderConfigurationRecord
                nalLengthSize = (csd0[21].toInt() and 0x03) + 1
                val numArrays = csd0[22].toInt() and 0xFF
                var pos = 23
                repeat(numArrays) {
                    if (pos + 3 > csd0.size) return@repeat
                    pos += 1 // array_completeness + NAL_unit_type
                    val numNalus = ((csd0[pos].toInt() and 0xFF) shl 8) or (csd0[pos + 1].toInt() and 0xFF)
                    pos += 2
                    repeat(numNalus) {
                        if (pos + 2 > csd0.size) return@repeat
                        val len = ((csd0[pos].toInt() and 0xFF) shl 8) or (csd0[pos + 1].toInt() and 0xFF)
                        pos += 2
                        if (pos + len <= csd0.size && len > 0) {
                            out.write(byteArrayOf(0, 0, 0, 1))
                            out.write(csd0, pos, len)
                        }
                        pos += len
                    }
                }
            }
        }
        return out.toByteArray() to nalLengthSize
    }

    private fun convertVideoSampleToAnnexB(
        data: ByteArray,
        nalLengthSize: Int,
        isHevc: Boolean,
        isKeyframe: Boolean,
        paramSetsAnnexB: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream(data.size + paramSetsAnnexB.size + 16)
        val hasAnnexB = (data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 0.toByte() && data[3] == 1.toByte()) ||
            (data.size >= 3 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 1.toByte())

        fun nalTypeOfHeader(headerByte: Byte): Int {
            val b = headerByte.toInt() and 0xFF
            return if (isHevc) (b ushr 1) and 0x3F else b and 0x1F
        }

        val firstNalType = if (hasAnnexB) {
            val scLen = if (data.size >= 4 && data[2] == 0.toByte()) 4 else 3
            if (data.size > scLen) nalTypeOfHeader(data[scLen]) else -1
        } else {
            if (data.size > nalLengthSize) nalTypeOfHeader(data[nalLengthSize]) else -1
        }
        val audType = if (isHevc) 35 else 9
        if (firstNalType != audType) {
            out.write(if (isHevc) HEVC_AUD else H264_AUD)
        }

        if (isKeyframe && paramSetsAnnexB.isNotEmpty()) {
            // Scan leading NALs (past AUD/SEI) to see if SPS/VPS is already in-band
            var alreadyHasParamSet = false
            if (hasAnnexB) {
                var idx = 0
                var scanned = 0
                while (idx + 3 < data.size && scanned < 6) {
                    if (data[idx] == 0.toByte() && data[idx + 1] == 0.toByte() &&
                        (data[idx + 2] == 1.toByte() || (idx + 3 < data.size && data[idx + 2] == 0.toByte() && data[idx + 3] == 1.toByte()))
                    ) {
                        val sc = if (data[idx + 2] == 1.toByte()) 3 else 4
                        if (idx + sc < data.size) {
                            val nt = nalTypeOfHeader(data[idx + sc])
                            if ((isHevc && (nt == 32 || nt == 33)) || (!isHevc && nt == 7)) {
                                alreadyHasParamSet = true
                                break
                            }
                            scanned++
                        }
                        idx += sc
                    } else {
                        idx++
                    }
                }
            } else {
                var pos = 0
                var scanned = 0
                while (pos + nalLengthSize < data.size && scanned < 6) {
                    var nalLen = 0
                    for (b in 0 until nalLengthSize) {
                        nalLen = (nalLen shl 8) or (data[pos + b].toInt() and 0xFF)
                    }
                    val hdrIdx = pos + nalLengthSize
                    if (nalLen <= 0 || hdrIdx + nalLen > data.size) break
                    val nt = nalTypeOfHeader(data[hdrIdx])
                    if ((isHevc && (nt == 32 || nt == 33)) || (!isHevc && nt == 7)) {
                        alreadyHasParamSet = true
                        break
                    }
                    scanned++
                    pos = hdrIdx + nalLen
                }
            }
            if (!alreadyHasParamSet) {
                out.write(paramSetsAnnexB)
            }
        }

        if (hasAnnexB) {
            out.write(data)
            return out.toByteArray()
        }

        var pos = 0
        var parsedAny = false
        while (pos + nalLengthSize <= data.size) {
            var nalLen = 0
            for (b in 0 until nalLengthSize) {
                nalLen = (nalLen shl 8) or (data[pos + b].toInt() and 0xFF)
            }
            pos += nalLengthSize
            if (nalLen <= 0 || pos + nalLen > data.size) break
            out.write(byteArrayOf(0, 0, 0, 1))
            out.write(data, pos, nalLen)
            pos += nalLen
            parsedAny = true
        }
        if (!parsedAny && data.isNotEmpty()) {
            out.write(byteArrayOf(0, 0, 0, 1))
            out.write(data)
        }
        return out.toByteArray()
    }

    private fun ensureAdtsFrame(
        data: ByteArray,
        profileMinusOne: Int,
        freqIdx: Int,
        chanCfg: Int,
    ): ByteArray {
        if (data.size >= 7 && data[0] == 0xFF.toByte() && (data[1].toInt() and 0xF6) == 0xF0) {
            return data
        }
        val frameLen = data.size + 7
        val out = ByteArray(frameLen)
        out[0] = 0xFF.toByte()
        out[1] = 0xF1.toByte() // MPEG-4 ADTS, no CRC
        out[2] = (((profileMinusOne and 0x03) shl 6) or ((freqIdx and 0x0F) shl 2) or ((chanCfg ushr 2) and 0x01)).toByte()
        out[3] = (((chanCfg and 0x03) shl 6) or ((frameLen ushr 11) and 0x03)).toByte()
        out[4] = ((frameLen ushr 3) and 0xFF).toByte()
        out[5] = (((frameLen and 0x07) shl 5) or 0x1F).toByte()
        out[6] = 0xFC.toByte()
        System.arraycopy(data, 0, out, 7, data.size)
        return out
    }

    private fun writeTimestamp5(out: ByteArray, off: Int, prefixNibble: Int, ts90k: Long) {
        out[off] = (prefixNibble or 0x01 or (((ts90k ushr 29) and 0x0E).toInt())).toByte()
        out[off + 1] = ((ts90k ushr 22) and 0xFF).toByte()
        out[off + 2] = (0x01 or (((ts90k ushr 14) and 0xFE).toInt())).toByte()
        out[off + 3] = ((ts90k ushr 7) and 0xFF).toByte()
        out[off + 4] = (0x01 or (((ts90k shl 1) and 0xFE).toInt())).toByte()
    }

    private fun buildPesPacket(streamId: Int, pts90k: Long, dts90k: Long? = null, payload: ByteArray): ByteArray {
        val hasDts = dts90k != null
        val headerDataLen = if (hasDts) 10 else 5
        val headerTotal = 9 + headerDataLen
        val totalLen = 3 + headerDataLen + payload.size
        val pesLenField = if (streamId == 0xE0 || totalLen > 65535) 0 else totalLen
        val out = ByteArray(headerTotal + payload.size)
        out[0] = 0x00
        out[1] = 0x00
        out[2] = 0x01
        out[3] = streamId.toByte()
        out[4] = ((pesLenField ushr 8) and 0xFF).toByte()
        out[5] = (pesLenField and 0xFF).toByte()
        out[6] = 0x80.toByte()
        out[7] = (if (hasDts) 0xC0 else 0x80).toByte()
        out[8] = headerDataLen.toByte()
        writeTimestamp5(out, 9, if (hasDts) 0x30 else 0x20, pts90k)
        if (hasDts && dts90k != null) {
            writeTimestamp5(out, 14, 0x10, dts90k)
        }
        System.arraycopy(payload, 0, out, headerTotal, payload.size)
        return out
    }

    private inline fun writePesAsTsPackets(
        out: ByteArrayOutputStream,
        pid: Int,
        pes: ByteArray,
        randomAccess: Boolean,
        pcr90k: Long?,
        nextCc: () -> Int,
    ) {
        var offset = 0
        var first = true
        while (offset < pes.size) {
            val pkt = ByteArray(188) { 0xFF.toByte() }
            pkt[0] = 0x47
            val pusi = if (first) 0x40 else 0x00
            pkt[1] = (pusi or ((pid ushr 8) and 0x1F)).toByte()
            pkt[2] = (pid and 0xFF).toByte()

            val needPcr = first && pcr90k != null
            val needRai = first && randomAccess && !needPcr
            val fixedAdaptBytes = when {
                needPcr -> 8 // adaptation_field_length(1) + flags(1) + 6 bytes PCR
                needRai -> 2 // adaptation_field_length(1) + flags(1)
                else -> 0
            }
            val remaining = pes.size - offset
            val maxPayload = 184 - fixedAdaptBytes
            val stuffBytes = if (remaining < maxPayload) maxPayload - remaining else 0
            val hasAdaptation = fixedAdaptBytes > 0 || stuffBytes > 0

            val cc = nextCc()
            pkt[3] = ((if (hasAdaptation) 0x30 else 0x10) or (cc and 0x0F)).toByte()

            var writePos = 4
            if (hasAdaptation) {
                val totalAdaptLen = (if (fixedAdaptBytes > 0) fixedAdaptBytes else 1) + stuffBytes
                // When only stuffing is needed and stuffBytes == 1, adaptation_field_length = 0 covers the 1 byte.
                if (fixedAdaptBytes == 0 && stuffBytes == 1) {
                    pkt[4] = 0x00
                    writePos = 5
                } else if (fixedAdaptBytes == 0) {
                    pkt[4] = (stuffBytes - 1).toByte()
                    pkt[5] = 0x00
                    writePos = 4 + stuffBytes
                } else {
                    val fieldLen = totalAdaptLen - 1
                    pkt[4] = fieldLen.toByte()
                    var flags = if (randomAccess && first) 0x40 else 0x00
                    if (needPcr) flags = flags or 0x10
                    pkt[5] = flags.toByte()
                    writePos = 6
                    if (needPcr && pcr90k != null) {
                        pkt[6] = ((pcr90k ushr 25) and 0xFF).toByte()
                        pkt[7] = ((pcr90k ushr 17) and 0xFF).toByte()
                        pkt[8] = ((pcr90k ushr 9) and 0xFF).toByte()
                        pkt[9] = ((pcr90k ushr 1) and 0xFF).toByte()
                        pkt[10] = ((((pcr90k and 0x01L) shl 7) or 0x7EL)).toByte()
                        pkt[11] = 0x00
                        writePos = 12
                    }
                    writePos += stuffBytes
                }
            }

            val copyLen = minOf(remaining, 188 - writePos)
            System.arraycopy(pes, offset, pkt, writePos, copyLen)
            out.write(pkt)
            offset += copyLen
            first = false
        }
    }

    private fun writePatPacket(out: ByteArrayOutputStream, session: RemuxSession) {
        val sec = byteArrayOf(
            0x00, // pointer_field
            0x00, // table_id = PAT
            0xB0.toByte(), 0x0D, // section_syntax_indicator + length = 13
            0x00, 0x01, // transport_stream_id
            0xC1.toByte(), // version=0, current=1
            0x00, 0x00, // section_number, last_section_number
            0x00, 0x01, // program_number = 1
            (0xE0 or ((PID_PMT ushr 8) and 0x1F)).toByte(),
            (PID_PMT and 0xFF).toByte(),
        )
        writePsiPacket(out, PID_PAT, sec, session.ccPat.also { session.ccPat = (it + 1) and 0x0F })
    }

    private fun writePmtPacket(out: ByteArrayOutputStream, session: RemuxSession) {
        val pcrPid = if (session.videoTrackIdx >= 0) PID_VIDEO else PID_AUDIO
        val esEntries = ByteArrayOutputStream(16)
        if (session.videoTrackIdx >= 0) {
            esEntries.write(session.videoStreamType)
            esEntries.write(0xE0 or ((PID_VIDEO ushr 8) and 0x1F))
            esEntries.write(PID_VIDEO and 0xFF)
            esEntries.write(0xF0)
            esEntries.write(0x00)
        }
        if (session.audioTrackIdx >= 0) {
            esEntries.write(session.audioStreamType)
            esEntries.write(0xE0 or ((PID_AUDIO ushr 8) and 0x1F))
            esEntries.write(PID_AUDIO and 0xFF)
            when (session.audioStreamType) {
                0x81 -> {
                    esEntries.write(0xF0)
                    esEntries.write(0x06)
                    esEntries.write(0x05)
                    esEntries.write(0x04)
                    esEntries.write('A'.code)
                    esEntries.write('C'.code)
                    esEntries.write('-'.code)
                    esEntries.write('3'.code)
                }
                0x87 -> {
                    esEntries.write(0xF0)
                    esEntries.write(0x06)
                    esEntries.write(0x05)
                    esEntries.write(0x04)
                    esEntries.write('E'.code)
                    esEntries.write('C'.code)
                    esEntries.write('-'.code)
                    esEntries.write('3'.code)
                }
                else -> {
                    esEntries.write(0xF0)
                    esEntries.write(0x00)
                }
            }
        }
        val esBytes = esEntries.toByteArray()
        val sectionLen = 9 + esBytes.size + 4
        val sec = ByteArray(1 + 3 + 9 + esBytes.size)
        sec[0] = 0x00 // pointer_field
        sec[1] = 0x02 // table_id = PMT
        sec[2] = (0xB0 or ((sectionLen ushr 8) and 0x0F)).toByte()
        sec[3] = (sectionLen and 0xFF).toByte()
        sec[4] = 0x00
        sec[5] = 0x01 // program_number = 1
        sec[6] = 0xC1.toByte()
        sec[7] = 0x00
        sec[8] = 0x00
        sec[9] = (0xE0 or ((pcrPid ushr 8) and 0x1F)).toByte()
        sec[10] = (pcrPid and 0xFF).toByte()
        sec[11] = 0xF0.toByte()
        sec[12] = 0x00
        System.arraycopy(esBytes, 0, sec, 13, esBytes.size)
        writePsiPacket(out, PID_PMT, sec, session.ccPmt.also { session.ccPmt = (it + 1) and 0x0F })
    }

    private fun writePsiPacket(out: ByteArrayOutputStream, pid: Int, pointerAndSectionNoCrc: ByteArray, cc: Int) {
        val crc = mpegCrc32(pointerAndSectionNoCrc, 1, pointerAndSectionNoCrc.size - 1)
        val pkt = ByteArray(188) { 0xFF.toByte() }
        pkt[0] = 0x47
        pkt[1] = (0x40 or ((pid ushr 8) and 0x1F)).toByte()
        pkt[2] = (pid and 0xFF).toByte()
        pkt[3] = (0x10 or (cc and 0x0F)).toByte()
        System.arraycopy(pointerAndSectionNoCrc, 0, pkt, 4, pointerAndSectionNoCrc.size)
        val crcPos = 4 + pointerAndSectionNoCrc.size
        pkt[crcPos] = ((crc ushr 24) and 0xFF).toByte()
        pkt[crcPos + 1] = ((crc ushr 16) and 0xFF).toByte()
        pkt[crcPos + 2] = ((crc ushr 8) and 0xFF).toByte()
        pkt[crcPos + 3] = (crc and 0xFF).toByte()
        out.write(pkt)
    }

    private fun mpegCrc32(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0xFFFFFFFF.toInt()
        for (i in offset until (offset + length)) {
            val idx = ((crc ushr 24) xor (data[i].toInt() and 0xFF)) and 0xFF
            crc = (crc shl 8) xor MPEG_CRC32_TABLE[idx]
        }
        return crc
    }
}
