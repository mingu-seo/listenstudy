package com.codro.listenstudy.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryMapperTest {
    @Test
    fun `library item clamps saved index and reports playback position`() {
        val item = LibraryMapper.toLibraryItem(
            document = StoredDocument(
                id = "doc-1",
                title = "Korean notes",
                sourceUri = "content://notes/1",
                sentenceCount = 4,
                lastSentenceIndex = 99,
                speed = 1.4f,
                createdAt = 10L,
                updatedAt = 20L,
            ),
        )

        assertEquals(3, item.lastSentenceIndex)
        assertEquals(100, item.progressPercent)
        assertEquals("위치 4 / 4", item.progressLabel)
    }

    @Test
    fun `display progress counts the current sentence consistently with the reader`() {
        fun item(count: Int, index: Int) = LibraryMapper.toLibraryItem(
            StoredDocument("id", "title", null, count, index, 1f, 1, 1),
        )

        assertEquals(25, item(4, 0).progressPercent)
        assertEquals(43, item(7, 2).progressPercent)
        assertEquals("위치 1 / 4", item(4, 0).progressLabel)
        assertEquals(0, item(0, 0).progressPercent)
        assertEquals("위치 0 / 0", item(0, 0).progressLabel)
        assertEquals(100, item(1, 0).progressPercent)
        assertEquals("위치 1 / 1", item(1, 0).progressLabel)
    }

    @Test
    fun `resume selection uses newest updated document and never autoplays`() {
        val resume = LibraryMapper.resume(
            documents = listOf(
                StoredDocument("old", "Old", null, 3, 1, 1f, 1, 10),
                StoredDocument("new", "New", null, 2, 1, 1.7f, 2, 30),
            ),
            sentences = listOf("one", "two"),
        )

        assertEquals("new", resume?.documentId)
        assertEquals(1, resume?.index)
        assertEquals(1.7f, resume?.speed)
        assertEquals(false, resume?.autoPlay)
    }

    @Test
    fun `resume selection breaks timestamp ties by id`() {
        val resume = LibraryMapper.resume(
            documents = listOf(
                StoredDocument("b", "B", null, 1, 0, 1f, 10, 20),
                StoredDocument("a", "A", null, 1, 0, 1f, 10, 20),
            ),
            sentences = listOf("one"),
        )

        assertEquals("a", resume?.documentId)
    }
}
