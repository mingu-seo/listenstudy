package com.codro.listenstudy.data.repository

data class StoredDocument(
    val id: String,
    val title: String,
    val sourceUri: String?,
    val sentenceCount: Int,
    val lastSentenceIndex: Int,
    val speed: Float,
    val createdAt: Long,
    val updatedAt: Long,
)

data class LibraryItem(
    val id: String,
    val title: String,
    val sentenceCount: Int,
    val lastSentenceIndex: Int,
    val progressPercent: Int,
    val progressLabel: String,
    val updatedAt: Long,
)

data class SavedDocument(
    val documentId: String,
    val title: String,
    val sentences: List<String>,
    val index: Int,
    val speed: Float,
    val autoPlay: Boolean = false,
)

object LibraryMapper {
    fun toLibraryItem(document: StoredDocument): LibraryItem {
        val count = document.sentenceCount.coerceAtLeast(0)
        val index = if (count == 0) 0 else document.lastSentenceIndex.coerceIn(0, count - 1)
        val displayedPosition = if (count == 0) 0 else index + 1
        return LibraryItem(
            id = document.id,
            title = document.title,
            sentenceCount = count,
            lastSentenceIndex = index,
            progressPercent = if (count <= 1) 0 else (index * 100 / (count - 1)),
            progressLabel = "위치 $displayedPosition / $count",
            updatedAt = document.updatedAt,
        )
    }

    fun resume(documents: List<StoredDocument>, sentences: List<String>): SavedDocument? {
        val document = documents.sortedWith(
            compareByDescending<StoredDocument> { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.id },
        ).firstOrNull() ?: return null
        if (sentences.isEmpty()) return null
        return SavedDocument(
            documentId = document.id,
            title = document.title,
            sentences = sentences,
            index = document.lastSentenceIndex.coerceIn(0, sentences.lastIndex),
            speed = document.speed.coerceIn(0.5f, 3f),
            autoPlay = false,
        )
    }
}
