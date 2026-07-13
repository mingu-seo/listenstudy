package com.codro.listenstudy.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationTest {
    @Test
    fun `reader and library can open independent settings and back returns to origin`() {
        val fromReader = AppNavigation.initial()
            .navigate(AppNavigationEvent.OpenSettings)
        assertEquals(AppDestination.Settings, fromReader.destination)
        assertEquals(AppDestination.Reader, fromReader.navigate(AppNavigationEvent.Back).destination)

        val fromLibrary = AppNavigation.initial()
            .navigate(AppNavigationEvent.OpenLibrary)
            .navigate(AppNavigationEvent.OpenSettings)
        assertEquals(AppDestination.Settings, fromLibrary.destination)
        assertEquals(AppDestination.Library, fromLibrary.navigate(AppNavigationEvent.Back).destination)
    }

    @Test
    fun `app has reader library and settings destinations but no home destination`() {
        assertEquals(
            setOf("Reader", "Library", "Settings"),
            AppDestination.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `library back opens reader`() {
        val state = AppNavigation.initial().navigate(AppNavigationEvent.OpenLibrary)
        assertEquals(AppDestination.Reader, state.navigate(AppNavigationEvent.Back).destination)
    }
}