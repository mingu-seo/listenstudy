package com.codro.listenstudy.ui

enum class AppDestination {
    Reader,
    Library,
    Settings,
    About,
}

enum class AppNavigationEvent {
    OpenReader,
    OpenLibrary,
    OpenSettings,
    OpenAbout,
    Back,
}

data class AppNavigation(
    val destination: AppDestination,
    private val settingsReturnDestination: AppDestination = AppDestination.Reader,
) {
    fun navigate(event: AppNavigationEvent): AppNavigation = when (event) {
        AppNavigationEvent.OpenReader -> copy(destination = AppDestination.Reader)
        AppNavigationEvent.OpenLibrary -> copy(destination = AppDestination.Library)
        AppNavigationEvent.OpenSettings -> copy(
            destination = AppDestination.Settings,
            settingsReturnDestination = destination.takeIf {
                it != AppDestination.Settings && it != AppDestination.About
            } ?: settingsReturnDestination,
        )
        // About is only reachable from Settings; opening it preserves the remembered Settings origin.
        AppNavigationEvent.OpenAbout -> copy(destination = AppDestination.About)
        AppNavigationEvent.Back -> when (destination) {
            AppDestination.About -> copy(destination = AppDestination.Settings)
            AppDestination.Settings -> copy(destination = settingsReturnDestination)
            AppDestination.Library -> copy(destination = AppDestination.Reader)
            AppDestination.Reader -> this
        }
    }

    companion object {
        fun initial() = AppNavigation(destination = AppDestination.Reader)
    }
}
