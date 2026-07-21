package com.codro.listenstudy.tts

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.codro.listenstudy.domain.tts.CloudAudioCache
import com.codro.listenstudy.security.AndroidKeystoreSecretStore
import com.codro.listenstudy.security.LegacySecretSource
import com.codro.listenstudy.security.SecretMigration
import com.codro.listenstudy.security.SecretStore
import com.codro.listenstudy.security.SharedPrefsLegacySecretSource
import com.codro.listenstudy.domain.tts.CloudCacheFirstSynthesizer
import com.codro.listenstudy.domain.tts.CloudErrorCategory
import com.codro.listenstudy.domain.tts.CloudErrorReport
import com.codro.listenstudy.domain.tts.CloudSynthesisGate
import com.codro.listenstudy.domain.tts.CloudSynthesisRequest
import com.codro.listenstudy.domain.tts.CloudTtsErrorPolicy
import com.codro.listenstudy.domain.tts.CloudTtsFailure
import com.codro.listenstudy.domain.tts.CloudTtsHttpError
import com.codro.listenstudy.domain.tts.CloudTtsRemote
import com.codro.listenstudy.domain.tts.PlaybackGenerationGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.Executors


/**
 * Stores the BYOK Google Cloud API key in Android Keystore-backed authenticated encryption.
 *
 * On construction it runs a one-time, idempotent migration of any legacy plaintext value from
 * `cloud_tts_private/google_api_key` into the secure store, removing the plaintext only after a
 * durable, verified secure write. Public behavior ([hasApiKey]/[apiKey]/[saveApiKey]/[deleteApiKey])
 * is unchanged. The raw key is never logged, returned in exceptions, or exposed via [toString].
 */
class CloudTtsSettings internal constructor(
    private val secretName: String,
    private val secure: SecretStore,
    private val legacy: LegacySecretSource,
) {
    constructor(context: Context) : this(
        SECRET_NAME,
        AndroidKeystoreSecretStore(context),
        SharedPrefsLegacySecretSource(context, LEGACY_PREFS, LEGACY_KEY),
    )

    init { SecretMigration(secretName, secure, legacy).migrateOnce() }

    fun hasApiKey(): Boolean = apiKey().isNotBlank()

    /**
     * Fully fail-closed. Every storage call is guarded so a failure yields blank rather than a crash
     * or a leak. The trimmed legacy plaintext is used only when `contains()` succeeds and reports no
     * secure payload — i.e. while a Keystore-unavailable migration is being retained. If a secure
     * payload exists but is unreadable, or `contains()` itself fails, this returns blank and never
     * resurrects legacy plaintext.
     */
    fun apiKey(): String {
        val hasSecure = runCatching { secure.contains(secretName) }.getOrNull() ?: return ""
        return if (hasSecure) {
            runCatching { secure.read(secretName) }.getOrNull().orEmpty()
        } else {
            runCatching { legacy.read()?.trim() }.getOrNull().orEmpty()
        }
    }

    /**
     * Returns true only when the key is trimmed, durably encrypted, verified readable, AND any
     * legacy plaintext copy is durably removed. If the legacy clear fails the caller is told the
     * operation did not fully complete, even though [hasApiKey] may already be true from the
     * successful secure write.
     */
    fun saveApiKey(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        return runCatching {
            secure.save(secretName, trimmed)
            if (secure.read(secretName) != trimmed) return@runCatching false
            legacy.clear()
        }.getOrDefault(false)
    }

    /** Returns true only when both the secure payload and any legacy plaintext are durably removed. */
    fun deleteApiKey(): Boolean {
        val secureDeleted = runCatching { secure.delete(secretName) }.getOrDefault(false)
        val legacyCleared = runCatching { legacy.clear() }.getOrDefault(false)
        return secureDeleted && legacyCleared
    }

    companion object {
        private const val LEGACY_PREFS = "cloud_tts_private"
        private const val LEGACY_KEY = "google_api_key"
        private const val SECRET_NAME = "google_cloud_tts_api_key"
    }
}

data class CloudCacheStats(val fileCount: Int, val totalBytes: Long)

class FileCloudAudioCache(context: Context) : CloudAudioCache {
    companion object { const val DEFAULT_MAX_BYTES = 128L * 1024 * 1024 }
    val directory = File(context.filesDir, "cloud_tts_cache").apply { mkdirs() }
    fun file(key: String) = File(directory, "$key.mp3")
    override suspend fun read(key: String): ByteArray? = file(key).takeIf { it.isFile }?.also { it.setLastModified(System.currentTimeMillis()) }?.readBytes()
    override suspend fun invalidate(key: String) { file(key).delete() }
    override suspend fun writeAtomically(key: String, bytes: ByteArray) {
        directory.mkdirs()
        val target = file(key)
        val temp = File(directory, ".$key-${System.nanoTime()}.tmp")
        temp.outputStream().use { stream -> stream.write(bytes); stream.fdSync() }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        trimTo(DEFAULT_MAX_BYTES)
    }
    fun stats(): CloudCacheStats = directory.listFiles()?.filter { it.isFile && it.extension == "mp3" }
        ?.let { CloudCacheStats(it.size, it.sumOf(File::length)) } ?: CloudCacheStats(0, 0)
    fun clear() { directory.listFiles()?.forEach { it.delete() } }
    fun trimTo(maxBytes: Long) {
        val files = directory.listFiles()?.filter { it.isFile && it.extension == "mp3" }?.sortedBy { it.lastModified() }.orEmpty()
        var total = files.sumOf(File::length)
        for (candidate in files) if (total > maxBytes) {
            val size = candidate.length()
            if (candidate.delete()) total -= size
        }
    }
    private fun java.io.FileOutputStream.fdSync() = fd.sync()
}

class GoogleCloudTtsRemote(private val context: Context) : CloudTtsRemote {
    override suspend fun synthesize(request: CloudSynthesisRequest, apiKey: String): ByteArray {
        val endpoint = URI("https", "texttospeech.googleapis.com", "/v1/text:synthesize", "key=$apiKey", null).toURL()
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Android-Package", context.packageName)
            signingCertificateSha1()?.let { setRequestProperty("X-Android-Cert", it) }
        }
        try {
            val body = JSONObject()
                .put("input", JSONObject().put("text", request.text))
                .put("voice", JSONObject().put("languageCode", "ko-KR").put("name", request.voiceId))
                .put("audioConfig", JSONObject().put("audioEncoding", request.format.uppercase()))
                .toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(body) }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val responseBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                // The body is reduced to a classification hint here and never carried as display text.
                throw CloudTtsFailure.Http(responseCode, CloudTtsHttpError.reason(responseBody))
            }
            val encoded = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optString("audioContent")
            if (encoded.isBlank()) throw CloudTtsFailure.InvalidAudio()
            return Base64.decode(encoded, Base64.DEFAULT)
        } finally { connection.disconnect() }
    }

    private fun signingCertificateSha1(): String? = runCatching {
        val certificate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return@runCatching null
        MessageDigest.getInstance("SHA-1").digest(certificate).joinToString("") { "%02X".format(it) }
    }.getOrNull()
}

class CloudTtsEngine(context: Context) {
    private val appContext = context.applicationContext
    private val cache = FileCloudAudioCache(appContext)
    private val synthesizer = CloudCacheFirstSynthesizer(cache, GoogleCloudTtsRemote(appContext))
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val generation = PlaybackGenerationGuard()
    private val prefetchGeneration = PlaybackGenerationGuard()
    private val speakGate = CloudSynthesisGate(generation, synthesizer::audio)
    private val prefetchGate = CloudSynthesisGate(prefetchGeneration, synthesizer::audio)
    @Volatile private var player: MediaPlayer? = null
    private var onDone: (String) -> Unit = {}
    private var onStatus: (String) -> Unit = {}
    private var onError: (String) -> Unit = {}
    private var onCloudError: (String, CloudErrorReport) -> Unit = { _, _ -> }

    fun speak(text: String, utteranceId: String, speed: Float, voiceId: String, apiKey: String) {
        stopPlayer(invalidate = true)
        // Speculative work must never delay or outlive the sentence the user just asked for: queued
        // prefetch shares this one worker, so a stale item would hold the thread against a live request.
        cancelPrefetch()
        val token = generation.current()
        status("클라우드 음성 준비 중…")
        executor.execute {
            val audio = try {
                // The gate re-checks the token here, on the worker, so a job superseded while it sat
                // in the queue is dropped before it can send (and pay for) a request.
                runBlocking { speakGate.audioIfCurrent(token, CloudSynthesisRequest(text, voiceId), apiKey) }
            } catch (_: CancellationException) {
                // The request was abandoned, which is not a failure and must never raise a panel.
                // Ends here rather than propagating: runBlocking is the boundary of coroutine
                // cancellation, and this runnable is handed to a bare executor, where a thrown
                // exception reaches Android's default uncaught handler and kills the process. This
                // engine's real cancellation signal is the generation token.
                return@execute
            } catch (failure: Throwable) {
                // Classify off the failure's structure; the throwable's own text never reaches the UI.
                if (generation.isCurrent(token)) cloudError(utteranceId, CloudTtsErrorPolicy.reportFor(failure), token)
                return@execute
            }
            // null = superseded: either nothing was sent, or the answer is for a sentence that moved on.
            if (audio == null) return@execute
            main.post { playFile(cache.file(audio.key), utteranceId, speed, audio.fromCache, token) }
        }
    }

    private fun playFile(file: File, utteranceId: String, speed: Float, fromCache: Boolean, token: Long) {
        if (!generation.isCurrent(token)) return
        runCatching {
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(file.absolutePath)
                setOnPreparedListener { prepared ->
                    if (!generation.isCurrent(token) || player !== prepared) {
                        prepared.release()
                        return@setOnPreparedListener
                    }
                    prepared.playbackParams = PlaybackParams().setSpeed(speed.coerceIn(0.5f, 3f))
                    status(if (fromCache) "로컬 캐시 재생 중" else "합성 완료 · 로컬 캐시 재생 중", token)
                    prepared.start()
                }
                setOnCompletionListener { completed ->
                    completed.release(); if (player === completed) player = null
                    main.post { generation.runIfCurrent(token) { onDone(utteranceId) } }
                }
                setOnErrorListener { failed, _, _ ->
                    failed.release(); if (player === failed) player = null
                    // A cached file that will not play is not a Google failure; it is unclassifiable
                    // from here, and Unknown correctly offers retry plus the phone voice.
                    cloudError(utteranceId, CloudTtsErrorPolicy.report(CloudErrorCategory.Unknown), token); true
                }
                prepareAsync()
            }
            player = mediaPlayer
        }.onFailure { cloudError(utteranceId, CloudTtsErrorPolicy.report(CloudErrorCategory.Unknown), token) }
    }

    fun stop() { cancelPrefetch(); stopPlayer(invalidate = true); status("정지됨") }
    /**
     * Single worker bounds prefetch concurrency; context changes invalidate queued work.
     *
     * Each call supersedes the previous plan, so an older plan's queued items cannot spend money on
     * sentences the user has already moved past. Failures are ignored on purpose — prefetch is
     * speculative, and a sentence that fails here will raise a real, classified error if and when the
     * user actually reaches it.
     */
    fun prefetch(texts: List<String>, voiceId: String, apiKey: String) {
        if (apiKey.isBlank() || texts.isEmpty()) return
        val token = prefetchGeneration.invalidateAndGet()
        executor.execute {
            for (text in texts.distinct().take(2)) {
                runCatching {
                    runBlocking { prefetchGate.audioIfCurrent(token, CloudSynthesisRequest(text, voiceId), apiKey) }
                }
            }
        }
    }
    fun cancelPrefetch() { prefetchGeneration.invalidateAndGet() }
    private fun stopPlayer(invalidate: Boolean) {
        if (invalidate) generation.invalidateAndGet()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }
    fun shutdown() { stopPlayer(true); executor.shutdownNow() }
    fun setOnDoneListener(listener: (String) -> Unit) { onDone = listener }
    fun setOnStatusListener(listener: (String) -> Unit) { onStatus = listener }
    fun setOnErrorListener(listener: (String) -> Unit) { onError = listener }
    /**
     * Receives the utterance id and the classified, user-safe report; [setOnErrorListener] still
     * drives the pause path. The id lets a caller tell a failed voice preview apart from a failed
     * document sentence — the two mean different things and need different recovery.
     */
    fun setOnCloudErrorListener(listener: (String, CloudErrorReport) -> Unit) { onCloudError = listener }
    fun stats(): CloudCacheStats = cache.stats()
    fun clearCache() { stop(); cache.clear() }
    private fun status(message: String) { main.post { onStatus(message) } }
    private fun status(message: String, token: Long) {
        main.post { generation.runIfCurrent(token) { onStatus(message) } }
    }
    /**
     * Publishes a classified failure for the sentence this [token] belongs to. Stale tokens are
     * dropped, so an error from an abandoned request can never pause or annotate a newer sentence.
     */
    private fun cloudError(utteranceId: String, report: CloudErrorReport, token: Long) {
        main.post {
            generation.runIfCurrent(token) {
                onStatus(report.title)
                onError(report.title)
                onCloudError(utteranceId, report)
            }
        }
    }
}
