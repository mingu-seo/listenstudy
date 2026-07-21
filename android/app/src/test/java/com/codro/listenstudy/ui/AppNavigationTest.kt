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
    fun `app has reader library settings and about destinations but no home destination`() {
        assertEquals(
            setOf("Reader", "Library", "Settings", "About"),
            AppDestination.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `library back opens reader`() {
        val state = AppNavigation.initial().navigate(AppNavigationEvent.OpenLibrary)
        assertEquals(AppDestination.Reader, state.navigate(AppNavigationEvent.Back).destination)
    }

    @Test
    fun `about opens from settings and back returns to settings`() {
        val atSettings = AppNavigation.initial().navigate(AppNavigationEvent.OpenSettings)

        val atAbout = atSettings.navigate(AppNavigationEvent.OpenAbout)
        assertEquals(AppDestination.About, atAbout.destination)

        val backToSettings = atAbout.navigate(AppNavigationEvent.Back)
        assertEquals(AppDestination.Settings, backToSettings.destination)
    }

    @Test
    fun `back from about then settings returns to the settings origin`() {
        // About is reached from Settings, which itself was opened from the Library. Backing all the
        // way out must land on the Library, not the Reader.
        val atAbout = AppNavigation.initial()
            .navigate(AppNavigationEvent.OpenLibrary)
            .navigate(AppNavigationEvent.OpenSettings)
            .navigate(AppNavigationEvent.OpenAbout)

        val backToSettings = atAbout.navigate(AppNavigationEvent.Back)
        assertEquals(AppDestination.Settings, backToSettings.destination)
        assertEquals(AppDestination.Library, backToSettings.navigate(AppNavigationEvent.Back).destination)
    }
}