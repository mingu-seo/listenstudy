package com.codro.listenstudy.domain.tts

import java.security.MessageDigest

enum class PlaybackMode(val label: String) {
    ON_DEVICE("휴대폰 TTS"),
    GOOGLE_STANDARD("Google Standard"),
    GOOGLE_WAVENET("Google WaveNet"),
}

data class CloudVoice(val id: String, val label: String, val mode: PlaybackMode)

object CloudVoiceCatalog {
    val voices: List<CloudVoice> = buildList {
        for (letter in 'A'..'D') add(CloudVoice("ko-KR-Standard-$letter", "Standard $letter", PlaybackMode.GOOGLE_STANDARD))
        for (letter in 'A'..'D') add(CloudVoice("ko-KR-Wavenet-$letter", "WaveNet $letter", PlaybackMode.GOOGLE_WAVENET))
    }
    val defaultVoice: CloudVoice = voices.first { it.id == "ko-KR-Wavenet-A" }
    fun forMode(mode: PlaybackMode): List<CloudVoice> = voices.filter { it.mode == mode }
    fun resolveForMode(mode: PlaybackMode, savedVoiceId: String?): CloudVoice =
        forMode(mode).firstOrNull { it.id == savedVoiceId }
            ?: forMode(mode).firstOrNull()
            ?: defaultVoice
}

data class CloudPlaybackDiagnostics(
    val engine: String,
    val voice: String,
    val cache: String,
) {
    companion object {
        fun create(
            mode: PlaybackMode,
            voice: CloudVoice,
            cacheFileCount: Int,
            cacheBytes: Long,
        ): CloudPlaybackDiagnostics {
            val model = when (mode) {
                PlaybackMode.GOOGLE_STANDARD -> "Standard"
                PlaybackMode.GOOGLE_WAVENET -> "WaveNet"
                PlaybackMode.ON_DEVICE -> "휴대폰"
            }
            return CloudPlaybackDiagnostics(
                engine = if (mode == PlaybackMode.ON_DEVICE) "휴대폰 TTS" else "Google Cloud TTS · $model",
                voice = voice.id,
                cache = "로컬 캐시 ${cacheFileCount}개 · ${formatBytes(cacheBytes)}",
            )
        }

        private fun formatBytes(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

data class CloudSynthesisRequest(val text: String, val voiceId: String, val format: String = "MP3")
data class CachedAudio(val bytes: ByteArray, val key: String, val fromCache: Boolean)

object CloudCacheKey {
    @Suppress("UNUSED_PARAMETER")
    fun create(text: String, voiceId: String, format: String, playbackSpeed: Float = 1f): String {
        val canonical = "${text.trim()}\u0000$voiceId\u0000${format.uppercase()}"
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

object CloudSettingsValidator {
    fun validate(mode: PlaybackMode, apiKey: String, cacheHit: Boolean = false): String? =
        if (mode != PlaybackMode.ON_DEVICE && !cacheHit && apiKey.isBlank()) "Google Cloud API 키를 먼저 저장해 주세요." else null
}

object CloudTtsHttpError {
    private val messagePattern = Regex("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")

    fun message(statusCode: Int, responseBody: String): String {
        val reason = messagePattern.find(responseBody)?.groupValues?.getOrNull(1)
            ?.replace("\\n", " ")
            ?.replace("\\\"", "\"")
            ?.take(400)
        return if (reason.isNullOrBlank()) {
            "Google Cloud TTS 요청 실패 (HTTP $statusCode). API 활성화와 키 제한을 확인하세요."
        } else {
            "Google Cloud TTS 요청 실패 (HTTP $statusCode): $reason"
        }
    }
}

interface CloudAudioCache {
    suspend fun read(key: String): ByteArray?
    suspend fun writeAtomically(key: String, bytes: ByteArray)
    suspend fun invalidate(key: String)
}

interface CloudTtsRemote {
    suspend fun synthesize(request: CloudSynthesisRequest, apiKey: String): ByteArray
}

class CloudCacheFirstSynthesizer(private val cache: CloudAudioCache, private val remote: CloudTtsRemote) {
    suspend fun audio(request: CloudSynthesisRequest, apiKey: String): CachedAudio {
        val key = CloudCacheKey.create(request.text, request.voiceId, request.format)
        cache.read(key)?.let {
            if (CloudAudioValidator.isValid(it, request.format)) return CachedAudio(it, key, true)
            cache.invalidate(key)
            if (apiKey.isBlank()) throw CloudTtsFailure.CacheCorrupt()
        }
        require(apiKey.isNotBlank()) { "Google Cloud API 키를 먼저 저장해 주세요." }
        val bytes = remote.synthesize(request, apiKey)
        require(CloudAudioValidator.isValid(bytes, request.format)) { "Google Cloud TTS가 비어 있거나 손상된 오디오를 반환했습니다." }
        cache.writeAtomically(key, bytes)
        return CachedAudio(bytes, key, false)
    }
}

object CloudAudioValidator {
    fun isValid(bytes: ByteArray, format: String): Boolean {
        if (bytes.size < 4) return false
        if (!format.equals("MP3", true)) return true
        val id3 = bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()
        val frame = bytes[0].toInt() and 0xff == 0xff && bytes[1].toInt() and 0xe0 == 0xe0
        return id3 || frame
    }
}

sealed class CloudTtsFailure(message: String) : Exception(message) {
    class CacheCorrupt : CloudTtsFailure("저장된 오디오가 손상되어 삭제했습니다. 다시 합성하려면 API 키를 저장하거나 휴대폰 TTS로 전환하세요.")
}

enum class CloudFailureKind { AUTH, QUOTA, NETWORK, PLAYBACK, OTHER }
data class CloudFailure(val kind: CloudFailureKind, val guidance: String)
object CloudFailureClassifier {
    fun http(status: Int) = when (status) { 401, 403 -> CloudFailureKind.AUTH; 429 -> CloudFailureKind.QUOTA; else -> CloudFailureKind.OTHER }
    fun network() = CloudFailure(CloudFailureKind.NETWORK, "네트워크를 확인하거나 캐시된 문장/휴대폰 TTS를 사용하세요.")
    fun playback() = CloudFailure(CloudFailureKind.PLAYBACK, "오디오를 다시 합성하거나 휴대폰 TTS로 전환하세요.")
}

class CloudPrefetchPlanner(private val maxAhead: Int = 2) {
    private var context: String? = null
    private var generation = 0L
    fun plan(sentences: List<String>, current: Int): List<String> = sentences.drop(current + 1).distinct().take(maxAhead.coerceIn(1, 3))
    fun contextToken(documentId: String, voiceId: String): Long {
        val next = "$documentId\u0000$voiceId"
        if (next != context) { context = next; generation++ }
        return generation
    }
}
