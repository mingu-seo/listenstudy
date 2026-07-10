package com.codro.listenstudy.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC, createdAt DESC, id ASC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC, createdAt DESC, id ASC")
    suspend fun getDocuments(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocument(id: String): DocumentEntity?

    @Upsert
    suspend fun upsertDocument(document: DocumentEntity)

    @Query("UPDATE documents SET lastSentenceIndex = :index, defaultSpeed = :speed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePlayback(id: String, index: Int, speed: Float, updatedAt: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)
}

@Dao
interface SentenceDao {
    @Query("SELECT * FROM sentences WHERE documentId = :documentId ORDER BY sentenceIndex ASC")
    fun observeSentences(documentId: String): Flow<List<SentenceEntity>>

    @Query("SELECT * FROM sentences WHERE documentId = :documentId ORDER BY sentenceIndex ASC")
    suspend fun getSentences(documentId: String): List<SentenceEntity>

    @Upsert
    suspend fun upsertSentences(sentences: List<SentenceEntity>)

    @Query("DELETE FROM sentences WHERE documentId = :documentId")
    suspend fun deleteSentences(documentId: String)
}


