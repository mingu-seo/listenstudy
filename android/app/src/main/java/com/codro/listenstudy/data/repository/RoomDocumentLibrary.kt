package com.codro.listenstudy.data.repository

import androidx.room.withTransaction
import com.codro.listenstudy.data.local.DocumentEntity
import com.codro.listenstudy.data.local.ListenStudyDatabase

import com.codro.listenstudy.data.local.SentenceEntity
import com.codro.listenstudy.domain.text.SentenceSpan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface DocumentLibrary {
    fun observeLibrary(): Flow<List<LibraryItem>>
    suspend fun importDocument(title: String, sourceUri: String?, sentences: List<SentenceSpan>): SavedDocument
    suspend fun loadDocument(id: String): SavedDocument?
    suspend fun loadMostRecent(): SavedDocument?
    suspend fun savePlayback(id: String, index: Int, speed: Float)
    suspend fun deleteDocument(id: String)
}

class RoomDocumentLibrary(
    private val database: ListenStudyDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : DocumentLibrary {
    override fun observeLibrary(): Flow<List<LibraryItem>> =
        database.documentDao().observeDocuments().map { documents ->
            documents.map { LibraryMapper.toLibraryItem(it.toStoredDocument()) }
        }

    override suspend fun importDocument(
        title: String,
        sourceUri: String?,
        sentences: List<SentenceSpan>,
    ): SavedDocument {
        require(sentences.isNotEmpty()) { "A document must contain at least one sentence" }
        val id = newId()
        val timestamp = now()
        database.withTransaction {
            database.documentDao().upsertDocument(
                DocumentEntity(
                    id = id,
                    title = title,
                    sourceUri = sourceUri,
                    localTextPath = null,
                    totalSentences = sentences.size,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    lastSentenceIndex = 0,
                    defaultEngine = "ON_DEVICE",
                    defaultVoiceId = null,
                    defaultSpeed = 1f,
                ),
            )
            database.sentenceDao().upsertSentences(sentences.map { sentence ->
                SentenceEntity(
                    id = "$id:${sentence.index}",
                    documentId = id,
                    sentenceIndex = sentence.index,
                    text = sentence.text,
                    startOffset = sentence.startOffset,
                    endOffset = sentence.endOffset,
                    cachedAudioPath = null,
                )
            })

        }
        return SavedDocument(id, title, sentences.map { it.text }, 0, 1f)
    }

    override suspend fun loadDocument(id: String): SavedDocument? {
        return database.withTransaction {
            val document = database.documentDao().getDocument(id) ?: return@withTransaction null
            val sentences = database.sentenceDao().getSentences(id).map { it.text }
            LibraryMapper.resume(listOf(document.toStoredDocument()), sentences)
        }
    }

    override suspend fun loadMostRecent(): SavedDocument? {
        return database.withTransaction {
            val document = database.documentDao().getDocuments().firstOrNull() ?: return@withTransaction null
            val sentences = database.sentenceDao().getSentences(document.id).map { it.text }
            LibraryMapper.resume(listOf(document.toStoredDocument()), sentences)
        }
    }

    override suspend fun savePlayback(id: String, index: Int, speed: Float) {
        val timestamp = now()
        database.withTransaction {
            val document = database.documentDao().getDocument(id) ?: return@withTransaction
            val safeIndex = index.coerceIn(0, (document.totalSentences - 1).coerceAtLeast(0))
            val safeSpeed = speed.coerceIn(0.5f, 3f)
            database.documentDao().updatePlayback(id, safeIndex, safeSpeed, timestamp)
        }
    }

    override suspend fun deleteDocument(id: String) {
        database.withTransaction {

            database.sentenceDao().deleteSentences(id)
            database.documentDao().deleteDocument(id)
        }
    }
}

private fun DocumentEntity.toStoredDocument() = StoredDocument(
    id = id,
    title = title,
    sourceUri = sourceUri,
    sentenceCount = totalSentences,
    lastSentenceIndex = lastSentenceIndex,
    speed = defaultSpeed,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
