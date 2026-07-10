package com.codro.listenstudy.domain.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSelectionMigrationTest {
    @Test
    fun legacy_or_missing_migration_version_resets_saved_engine_and_voice() {
        assertTrue(TtsSelectionMigration.shouldResetSavedSelection(appliedVersion = 0))
        assertTrue(TtsSelectionMigration.shouldResetSavedSelection(appliedVersion = -1))
    }

    @Test
    fun previous_migration_version_resets_voice_selected_by_older_apk() {
        assertTrue(TtsSelectionMigration.shouldResetSavedSelection(appliedVersion = 1))
    }

    @Test
    fun current_migration_version_keeps_new_user_selection() {
        assertFalse(
            TtsSelectionMigration.shouldResetSavedSelection(
                appliedVersion = TtsSelectionMigration.CURRENT_VERSION,
            ),
        )
    }
}
