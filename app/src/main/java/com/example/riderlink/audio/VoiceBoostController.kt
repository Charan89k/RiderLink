package com.example.riderlink.audio

import android.util.Log
import com.example.riderlink.domain.model.VoiceBoost
import io.livekit.android.room.track.RemoteAudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import livekit.org.webrtc.AudioTrackSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Applies Voice Boost to incoming rider audio.
 *
 * Each remote track gets a read-only sink that measures its true peak, and its
 * own limiter deciding how much of the requested boost is safe. The resulting
 * gain is applied with [RemoteAudioTrack.setVolume], which scales the track
 * inside WebRTC -- before the mixer, and nowhere near the music stream. That
 * placement is what keeps Voice Boost from amplifying the rider's whole phone,
 * and it leaves the existing ducking behaviour untouched.
 *
 * Per-track also means privacy is unaffected: a track the rider is not
 * subscribed to produces no audio to boost, so private mode keeps working
 * exactly as before.
 */
class VoiceBoostController(private val scope: CoroutineScope) {

    private class TrackState(val track: RemoteAudioTrack) {
        val limiter = VoiceBoostLimiter()

        /**
         * Peak of the most recent frame, as a scaled integer.
         *
         * The audio callback runs on WebRTC's playback thread and must not
         * block, so it only writes this number. All decisions happen on the
         * control loop below.
         */
        val latestPeak = AtomicInteger(0)
        var appliedGain = 1.0f
        var sink: AudioTrackSink? = null

        /** Throttles the diagnostic log to something readable in a bug report. */
        var lastLoggedAt = 0L
    }

    private val tracks = ConcurrentHashMap<String, TrackState>()

    @Volatile
    private var boost: VoiceBoost = VoiceBoost.DEFAULT

    private var controlJob: Job? = null

    /** Changes the boost level. Takes effect on the next control tick. */
    fun setBoost(level: VoiceBoost) {
        if (boost == level) return
        boost = level
        Log.d(TAG, "Voice boost set to ${level.name} (${level.percentLabel})")

        if (level.isActive) {
            startControlLoop()
        } else {
            stopControlLoop()
            // Hand every track back at unity so turning boost off is immediate
            // and complete, rather than leaving the last limited gain in place.
            tracks.values.forEach { state ->
                state.limiter.reset()
                state.appliedGain = 1.0f
                runCatching { state.track.setVolume(1.0) }
            }
        }
    }

    /** Starts boosting a newly subscribed remote track. */
    fun attach(trackSid: String, track: RemoteAudioTrack) {
        if (tracks.containsKey(trackSid)) return

        val state = TrackState(track)
        val sink = AudioTrackSink { audioData, bitsPerSample, _, channels, frames, _ ->
            state.latestPeak.set(measurePeak(audioData, bitsPerSample, channels, frames))
        }
        state.sink = sink
        tracks[trackSid] = state

        runCatching { track.addSink(sink) }
            .onFailure { Log.w(TAG, "Could not tap track $trackSid for boost", it) }

        Log.d(TAG, "Voice boost attached to track $trackSid")
        if (boost.isActive) startControlLoop()
    }

    /** Stops boosting a track that has gone away. */
    fun detach(trackSid: String) {
        val state = tracks.remove(trackSid) ?: return
        state.sink?.let { sink -> runCatching { state.track.removeSink(sink) } }
        Log.d(TAG, "Voice boost detached from track $trackSid")
        if (tracks.isEmpty()) stopControlLoop()
    }

    /** Drops every track, for disconnect. */
    fun detachAll() {
        tracks.keys.toList().forEach(::detach)
        stopControlLoop()
    }

    /**
     * Applies limiter decisions off the audio thread.
     *
     * Calling setVolume from inside the audio callback would put a cross-thread
     * hop on the playback path. Instead the callback leaves a peak behind and
     * this loop, running at roughly the limiter's attack time, acts on it.
     */
    private fun startControlLoop() {
        if (controlJob?.isActive == true) return
        controlJob = scope.launch {
            var previousTickNanos = System.nanoTime()
            while (isActive) {
                val target = boost.gain

                // Measure how much time actually passed rather than assuming the
                // loop hit its nominal interval. Under load it does not, and
                // feeding the limiter a fixed 10ms made its release constant
                // wrong by whatever factor the loop was running late by --
                // leaving audio under-boosted for seconds after a loud word.
                val now = System.nanoTime()
                val elapsedMs = ((now - previousTickNanos) / 1_000_000f)
                    .coerceIn(MIN_TICK_MS, MAX_TICK_MS)
                previousTickNanos = now
                tracks.values.forEach { state ->
                    val peak = state.latestPeak.get() / PEAK_SCALE
                    val gain = state.limiter.process(peak, target, elapsedMs)

                    // setVolume crosses into native WebRTC, so only call it when
                    // the change is big enough for anyone to hear.
                    if (abs(gain - state.appliedGain) > GAIN_EPSILON) {
                        state.appliedGain = gain
                        runCatching { state.track.setVolume(gain.toDouble()) }
                            .onFailure { Log.w(TAG, "setVolume failed", it) }
                    }

                    // One line a second, so a rider reporting "it sounds bad"
                    // leaves behind enough to tell gain from limiting.
                    val now = System.currentTimeMillis()
                    if (now - state.lastLoggedAt >= LOG_INTERVAL_MS) {
                        state.lastLoggedAt = now
                        Log.d(
                            TAG,
                            "peak=%.2f target=%.2f gain=%.2f".format(peak, target, gain),
                        )
                    }
                }
                delay(CONTROL_INTERVAL_MS)
            }
        }
    }

    private fun stopControlLoop() {
        controlJob?.cancel()
        controlJob = null
    }

    private companion object {
        const val TAG = "VoiceBoost"

        /** Close to the limiter's attack time, so it can actually act that fast. */
        const val CONTROL_INTERVAL_MS = 10L

        const val GAIN_EPSILON = 0.02f
        const val LOG_INTERVAL_MS = 1_000L

        /**
         * Bounds on the measured tick, so a scheduling stall cannot hand the
         * limiter a single enormous frame and undo its limiting in one step.
         */
        const val MIN_TICK_MS = 1f
        const val MAX_TICK_MS = 200f
        const val PEAK_SCALE = 10_000f

        /**
         * Highest absolute sample in the frame, as peak * [PEAK_SCALE].
         *
         * Runs on the audio thread, so it allocates nothing and only reads the
         * buffer. Anything other than 16-bit PCM is reported as silent rather
         * than misread, which leaves the limiter holding the requested gain.
         */
        fun measurePeak(buffer: ByteBuffer, bitsPerSample: Int, channels: Int, frames: Int): Int {
            if (bitsPerSample != 16 || frames <= 0 || channels <= 0) return 0

            val samples = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val count = minOf(samples.remaining(), frames * channels)

            var peak = 0
            for (i in 0 until count) {
                val magnitude = abs(samples.get(i).toInt())
                if (magnitude > peak) peak = magnitude
            }
            return ((peak / 32768f) * PEAK_SCALE).toInt()
        }
    }
}
