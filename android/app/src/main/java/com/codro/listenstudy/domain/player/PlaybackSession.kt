package com.codro.listenstudy.domain.player

import kotlinx.coroutines.flow.StateFlow

sealed interface PlaybackSessionCommand {
    data object Play : PlaybackSessionCommand
    data object Pause : PlaybackSessionCommand
    data object Previous : PlaybackSessionCommand
    data object Next : PlaybackSessionCommand
    data class Jump(val index: Int) : PlaybackSessionCommand
    data class SetSpeed(val speed: Float) : PlaybackSessionCommand
    data class ReplaceDocument(val sentences: List<String>, val index: Int = 0, val speed: Float = 1f) : PlaybackSessionCommand
}

/** Android-independent owner/coordinator for one playback controller. */
class PlaybackSession(
    sentences: List<String>,
    speak: suspend (String, String, Float) -> Unit,
    stopSpeaking: () -> Unit = {},
) {
    private val controller = PlaybackController(sentences, speak, stopSpeaking)
    val state: StateFlow<PlaybackState> = controller.state

    suspend fun execute(command: PlaybackSessionCommand) {
        when (command) {
            PlaybackSessionCommand.Play -> controller.play()
            PlaybackSessionCommand.Pause -> controller.pause()
            PlaybackSessionCommand.Previous -> controller.previous(state.value.status == PlaybackStatus.Playing)
            PlaybackSessionCommand.Next -> controller.next(state.value.status == PlaybackStatus.Playing)
            is PlaybackSessionCommand.Jump -> controller.jumpTo(command.index)
            is PlaybackSessionCommand.SetSpeed -> controller.setSpeed(command.speed)
            is PlaybackSessionCommand.ReplaceDocument -> restore(command.sentences, command.index, command.speed)
        }
    }

    fun restore(sentences: List<String>, index: Int, speed: Float) {
        controller.replaceSentences(sentences)
        controller.setSpeed(speed)
        controller.jumpTo(index) // restoration is deliberately paused
    }

    suspend fun onSentenceDone(utteranceId: String) = controller.onSentenceDone(utteranceId)
    fun onPlaybackError() = controller.onPlaybackError()
    fun stop() = controller.stop()
}
