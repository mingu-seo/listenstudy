package com.codro.listenstudy.ui

enum class AppDestination {
    Reader,
    Library,
    Settings,
}

enum class AppNavigationEvent {
    OpenReader,
    OpenLibrary,
    OpenSettings,
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
            settingsReturnDestination = destination.takeIf { it != AppDestination.Settings }
                ?: settingsReturnDestination,
        )
        AppNavigationEvent.Back -> when (destination) {
            AppDestination.Settings -> copy(destination = settingsReturnDestination)
            AppDestination.Library -> copy(destination = AppDestination.Reader)
            AppDestination.Reader -> this
        }
    }

    companion object {
        fun initial() = AppNavigation(destination = AppDestination.Reader)
    }
}
