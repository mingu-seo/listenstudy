package com.codro.listenstudy.domain.player

sealed class PlaybackServiceCommand {
    data object PlayPause : PlaybackServiceCommand()
    data object Previous : PlaybackServiceCommand()
    data object Next : PlaybackServiceCommand()

    companion object {
        const val ACTION_PLAY_PAUSE = "com.codro.listenstudy.action.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.codro.listenstudy.action.PREVIOUS"
        const val ACTION_NEXT = "com.codro.listenstudy.action.NEXT"
        const val ACTION_UPDATE = "com.codro.listenstudy.action.UPDATE_NOTIFICATION"
        const val EXTRA_TITLE = "com.codro.listenstudy.extra.TITLE"
        const val EXTRA_STATUS = "com.codro.listenstudy.extra.STATUS"
        const val EXTRA_PROGRESS = "com.codro.listenstudy.extra.PROGRESS"

        fun fromAction(action: String?): PlaybackServiceCommand? = when (action) {
            ACTION_PLAY_PAUSE -> PlayPause
            ACTION_PREVIOUS -> Previous
            ACTION_NEXT -> Next
            else -> null
        }
    }
}
