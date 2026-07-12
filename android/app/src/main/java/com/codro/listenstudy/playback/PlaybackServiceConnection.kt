package com.codro.listenstudy.playback


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.codro.listenstudy.domain.player.PlaybackStatus

/** Thread-safe process-local facade. Ownership is identified by service instance identity. */
class PlaybackServiceFacade(private val maxPending: Int = 32) {
    private val mutableState = MutableStateFlow(PlaybackServiceUiState())
    val state: StateFlow<PlaybackServiceUiState> = mutableState
    private var owner: Any? = null
    private var executor: ((ServiceCommand) -> Unit)? = null
    private val pending = ArrayDeque<ServiceCommand>()

    @Synchronized fun attach(owner: Any, initial: PlaybackServiceUiState, execute: (ServiceCommand) -> Unit) {
        this.owner = owner
        mutableState.value = initial
        executor = execute
        while (pending.isNotEmpty()) execute(pending.removeFirst())
    }
    @Synchronized fun publish(owner: Any, value: PlaybackServiceUiState) {
        if (this.owner === owner) mutableState.value = value
    }
    @Synchronized fun detach(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        executor = null
        pending.clear() // never replay actions into a later service generation
        mutableState.value = mutableState.value.copy(
            playback = mutableState.value.playback.copy(status = PlaybackStatus.Paused),
            ttsStatus = "재생 서비스 연결이 끊어졌습니다.",
        )
    }
    @Synchronized fun dispatch(command: ServiceCommand) {
        executor?.invoke(command) ?: run {
            if (pending.size == maxPending) pending.removeFirst()
            pending.addLast(command)
        }
    }
}

object PlaybackServiceConnection {
    private val facade = PlaybackServiceFacade()
    val uiState: StateFlow<PlaybackServiceUiState> = facade.state
    fun attach(owner: Any, initial: PlaybackServiceUiState, execute: (ServiceCommand) -> Unit) = facade.attach(owner, initial, execute)
    fun publish(owner: Any, value: PlaybackServiceUiState) = facade.publish(owner, value)
    fun detach(owner: Any) = facade.detach(owner)
    fun dispatch(command: ServiceCommand) = facade.dispatch(command)
}
