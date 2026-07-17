package com.codro.listenstudy.domain.tts

/**
 * Stops superseded synthesis jobs before they reach the network.
 *
 * Cloud jobs are queued onto a single worker, so a job can wait behind an in-flight request while
 * the user taps 다시 시도, skips a sentence, stops, or switches to the phone voice. Every one of those
 * bumps the generation. Checking the generation only after synthesis returns is too late: the
 * request has already been sent and billed, and it also delays the sentence the user is actually
 * waiting for. This gate makes the check happen at the moment the job starts.
 *
 * The post-check is kept as well, for the window where the job was still wanted when it started but
 * was superseded while in flight: that request cannot be un-sent, but its audio must not be played
 * against a sentence that has moved on.
 *
 * Failures are deliberately not caught — a real failure on the current sentence has to reach
 * [CloudTtsErrorPolicy].
 */
class CloudSynthesisGate(
    private val guard: PlaybackGenerationGuard,
    private val audio: suspend (CloudSynthesisRequest, String) -> CachedAudio,
) {
    /** Returns null when the job was superseded; [audio] is then never invoked. */
    suspend fun audioIfCurrent(token: Long, request: CloudSynthesisRequest, apiKey: String): CachedAudio? {
        if (!guard.isCurrent(token)) return null
        val result = audio(request, apiKey)
        return result.takeIf { guard.isCurrent(token) }
    }
}
