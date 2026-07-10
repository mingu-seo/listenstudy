package com.codro.listenstudy.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceUri: String?,
    val localTextPath: String?,
    val totalSentences: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSentenceIndex: Int,
    val defaultEngine: String,
    val defaultVoiceId: String?,
    val defaultSpeed: Float,
)

@Entity(
    tableName = "sentences",
    indices = [Index(value = ["documentId", "sentenceIndex"], unique = true)],
)
data class SentenceEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val sentenceIndex: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val cachedAudioPath: String?,
)


