package com.codro.listenstudy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        SentenceEntity::class,

    ],
    version = 1,
    exportSchema = true,
)
abstract class ListenStudyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun sentenceDao(): SentenceDao

}
