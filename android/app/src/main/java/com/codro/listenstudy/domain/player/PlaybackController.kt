package com.codro.listenstudy.domain.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlin.math.min

class PlaybackController(
    sentences: List<String>,
    private val speak: suspend (text: String, utteranceId: String, speed: Float) -> Unit = { _, _, _ -> },
    private val stopSpeaking: () -> Unit = {},
) {
    private val _state = MutableStateFlow(PlaybackState(sentences = sentences))
    val state: StateFlow<PlaybackState> = _state
    private var documentGeneration = 0L
    private var playAttemptGeneration = 0L

    suspend fun play() {
        val sentence = _state.value.currentSentence ?: return
        playAttemptGeneration += 1
        _state.update { it.copy(status = PlaybackStatus.Playing) }
        speak(sentence, utteranceIdFor(_state.value.currentIndex), _state.value.speed)
    }

    fun pause() {
        invalidatePlayAttempt()
        stopSpeaking()
        _state.update { it.copy(status = PlaybackStatus.Paused) }
    }

    fun stop() {
        invalidatePlayAttempt()
        stopSpeaking()
        _state.update { it.copy(status = PlaybackStatus.Idle) }
    }

    fun replaceSentences(sentences: List<String>) {
        invalidatePlayAttempt()
        stopSpeaking()
        documentGeneration += 1
        _state.update {
            it.copy(
                sentences = sentences,
                currentIndex = 0,
                status = PlaybackStatus.Idle,
            )
        }
    }

    suspend fun previous(autoPlay: Boolean = false) {
        invalidatePlayAttempt()
        stopSpeaking()
        val previousIndex = max(0, _state.value.currentIndex - 1)
        _state.update { it.copy(currentIndex = previousIndex, status = if (autoPlay) PlaybackStatus.Playing else PlaybackStatus.Paused) }
        if (autoPlay) play()
    }

    suspend fun next(autoPlay: Boolean = false) {
        advanceToNext(autoPlay = autoPlay, stopCurrentSpeech = true)
    }

    private suspend fun advanceToNext(autoPlay: Boolean, stopCurrentSpeech: Boolean) {
        if (stopCurrentSpeech) {
            invalidatePlayAttempt()
            stopSpeaking()
        }
        val nextIndex = _state.value.currentIndex + 1
        if (nextIndex >= _state.value.sentences.size) {
            _state.update { it.copy(status = PlaybackStatus.Completed) }
            return
        }
        _state.update { it.copy(currentIndex = nextIndex, status = if (autoPlay) PlaybackStatus.Playing else PlaybackStatus.Paused) }
        if (autoPlay) play()
    }

    fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed.coerceIn(0.5f, 3.0f)) }
    }

    fun jumpTo(index: Int) {
        invalidatePlayAttempt()
        stopSpeaking()
        _state.update {
            it.copy(
                currentIndex = min(max(index, 0), max(it.sentences.lastIndex, 0)),
                status = PlaybackStatus.Paused,
            )
        }
    }

    fun onPlaybackError() {
        _state.update { it.copy(status = PlaybackStatus.Paused) }
    }

    suspend fun onSentenceDone(utteranceId: String) {
        val snapshot = _state.value
        val identity = utteranceId.removePrefix(UTTERANCE_PREFIX).split('_')
        val doneGeneration = identity.getOrNull(0)?.toLongOrNull()
        val doneAttempt = identity.getOrNull(1)?.toLongOrNull()
        val doneIndex = identity.getOrNull(2)?.toIntOrNull()
        if (
            snapshot.status == PlaybackStatus.Playing &&
            doneGeneration == documentGeneration &&
            doneAttempt == playAttemptGeneration &&
            doneIndex == snapshot.currentIndex
        ) {
            advanceToNext(autoPlay = true, stopCurrentSpeech = false)
        }
    }

    private fun utteranceIdFor(index: Int): String =
        "$UTTERANCE_PREFIX${documentGeneration}_${playAttemptGeneration}_$index"

    private fun invalidatePlayAttempt() {
        playAttemptGeneration += 1
    }

    companion object {
        const val UTTERANCE_PREFIX = "listenstudy_sentence_"
    }
}
