package app.bonedle.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation destinations. The Play route lives with its screen (feature.play.PlayRoute).

@Serializable
data object HomeRoute

@Serializable
data object StatsRoute

@Serializable
data object ArchiveRoute

@Serializable
data object SettingsRoute

@Serializable
data object CreditsRoute

/** The Bonedle Plus sheet. */
@Serializable
data object PlusRoute

/**
 * The result of a finished board, by game key. [fresh] is true straight after solving it (the
 * only time the screen may offer the reminder or ask for a rating).
 */
@Serializable
data class SolvedRoute(val gameKey: String, val fresh: Boolean = false)

/** Play-route sources that mark a tutorial run: the first launch, or "How to play" later. */
object TutorialSource {
    const val ONBOARDING = "onboarding"
    const val HOW_TO = "howto"
}
