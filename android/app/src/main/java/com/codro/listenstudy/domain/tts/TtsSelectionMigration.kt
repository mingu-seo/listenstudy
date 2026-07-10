package com.codro.listenstudy.domain.tts

object TtsSelectionMigration {
    const val CURRENT_VERSION = 2

    fun shouldResetSavedSelection(appliedVersion: Int): Boolean =
        appliedVersion < CURRENT_VERSION
}
