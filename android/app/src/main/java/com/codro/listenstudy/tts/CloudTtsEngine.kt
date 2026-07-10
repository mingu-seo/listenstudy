package com.codro.listenstudy.tts

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.codro.listenstudy.domain.tts.CloudAudioCache
import com.codro.listenstudy.domain.tts.CloudCacheFirstSynthesizer
import com.codro.listenstudy.domain.tts.CloudSynthesisRequest
import com.codro.listenstudy.domain.tts.CloudTtsHttpError
import com.codro.listenstudy.domain.tts.CloudTtsRemote
import com.codro.listenstudy.domain.tts.PlaybackGenerationGuard
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.Executors


class CloudTtsSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun hasApiKey(): Boolean = apiKey().isNotBlank()
    fun apiKey(): String = prefs.getString(KEY, "").orEmpty()
    fun saveApiKey(value: String) { prefs.edit().putString(KEY, value.trim()).apply() }
    fun deleteApiKey() { prefs.edit().remove(KEY).apply() }
    companion object { private const val PREFS = "cloud_tts_private"; private const val KEY = "google_api_key" }
}

data class CloudCacheStats(val fileCount: Int, val totalBytes: Long)

class FileCloudAudioCache(context: Context) : CloudAudioCache {
    val directory = File(context.filesDir, "cloud_tts_cache").apply { mkdirs() }
    fun file(key: String) = File(directory, "$key.mp3")
    override suspend fun read(key: String): ByteArray? = file(key).takeIf { it.isFile }?.readBytes()
    override suspend fun writeAtomically(key: String, bytes: ByteArray) {
        directory.mkdirs()
        val target = file(key)
        val temp = File(directory, ".$key-${System.nanoTime()}.tmp")
        temp.outputStream().use { stream -> stream.write(bytes); stream.fdSync() }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }
    fun stats(): CloudCacheStats = directory.listFiles()?.filter { it.isFile && it.extension == "mp3" }
        ?.let { CloudCacheStats(it.size, it.sumOf(File::length)) } ?: CloudCacheStats(0, 0)
    fun clear() { directory.listFiles()?.forEach { it.delete() } }
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
                error(CloudTtsHttpError.message(responseCode, responseBody))
            }
            val encoded = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optString("audioContent")
            if (encoded.isBlank()) error("Google Cloud TTS 응답에 오디오가 없습니다.")
            return Base64.decode(encoded, Base64.DEFAULT)
        } finally { connection.disconnect() }
    }

    private fun signingCertificateSha1(): String? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val certificate = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: return@runCatching null
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
    @Volatile private var player: MediaPlayer? = null
    private var onDone: (String) -> Unit = {}
    private var onStatus: (String) -> Unit = {}
    private var onError: (String) -> Unit = {}

    fun speak(text: String, utteranceId: String, speed: Float, voiceId: String, apiKey: String) {
        stopPlayer(invalidate = true)
        val token = generation.current()
        status("클라우드 음성 준비 중…")
        executor.execute {
            runCatching { runBlocking { synthesizer.audio(CloudSynthesisRequest(text, voiceId), apiKey) } }
                .onSuccess { audio ->
                    if (!generation.isCurrent(token)) return@onSuccess
                    main.post { playFile(cache.file(audio.key), utteranceId, speed, audio.fromCache, token) }
                }.onFailure { failure ->
                    if (generation.isCurrent(token)) {
                        val rawMessage = failure.message ?: "클라우드 TTS 오류: ${failure.javaClass.simpleName}"
                        val safeMessage = if (apiKey.isNotBlank()) rawMessage.replace(apiKey, "••••") else rawMessage
                        error(safeMessage, token)
                    }
                }
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
                    error("클라우드 오디오 재생 오류", token); true
                }
                prepareAsync()
            }
            player = mediaPlayer
        }.onFailure { error("클라우드 오디오 재생 실패: ${it.javaClass.simpleName}", token) }
    }

    fun stop() { stopPlayer(invalidate = true); status("정지됨") }
    private fun stopPlayer(invalidate: Boolean) {
        if (invalidate) generation.invalidateAndGet()
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }
    fun shutdown() { stopPlayer(true); executor.shutdownNow() }
    fun setOnDoneListener(listener: (String) -> Unit) { onDone = listener }
    fun setOnStatusListener(listener: (String) -> Unit) { onStatus = listener }
    fun setOnErrorListener(listener: (String) -> Unit) { onError = listener }
    fun stats(): CloudCacheStats = cache.stats()
    fun clearCache() { stop(); cache.clear() }
    private fun status(message: String) { main.post { onStatus(message) } }
    private fun status(message: String, token: Long) {
        main.post { generation.runIfCurrent(token) { onStatus(message) } }
    }
    private fun error(message: String, token: Long) {
        main.post {
            generation.runIfCurrent(token) {
                onStatus(message)
                onError(message)
            }
        }
    }
}
