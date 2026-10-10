package app.bonedle.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.bonedle.data.game.BoardStatus
import app.bonedle.data.game.GameId
import app.bonedle.data.game.GameSource
import app.bonedle.data.links.PendingLink
import app.bonedle.domain.BoardLink
import app.bonedle.engine.daily.CalendarDate
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.tutorial.TutorialBoards
import app.bonedle.feature.archive.ArchiveDestination
import app.bonedle.feature.home.HomeDestination
import app.bonedle.feature.play.PlayDestination
import app.bonedle.feature.play.PlayRoute
import app.bonedle.feature.plus.PlusDestination
import app.bonedle.feature.settings.CreditsScreen
import app.bonedle.feature.settings.SettingsDestination
import app.bonedle.feature.solved.SolvedDestination
import app.bonedle.feature.stats.StatsDestination
import app.bonedle.ui.theme.BonedleMotion
import app.bonedle.ui.theme.BonedleTheme

/**
 * App navigation. First launch starts in the tutorial, which ends in today's Easy daily with
 * Home underneath; later launches start on Home. Solving a board replaces Play with Solved.
 * A board from a challenge link ([pendingLink]) opens over Home as soon as the player is past
 * the tutorial; for a new player it takes the place of today's Easy daily.
 *
 * @param onboarded whether the tutorial was already finished or skipped.
 * @param onOnboarded records that the first-launch tutorial is done ([skipped] or completed).
 * @param onLinkOpened the pending link was opened and can be cleared.
 */
@Composable
fun BonedleNavHost(
    onboarded: Boolean,
    today: () -> CalendarDate,
    onOnboarded: (skipped: Boolean) -> Unit,
    onExit: () -> Unit,
    pendingLink: PendingLink? = null,
    onLinkOpened: (BoardLink) -> Unit = {},
    nav: NavHostController = rememberNavController(),
) {
    val motion = BonedleTheme.motion
    val start: Any = if (onboarded) HomeRoute else PlayRoute(GameId.Tutorial(0).key, source = TutorialSource.ONBOARDING)
    var pastTutorial by rememberSaveable { mutableStateOf(onboarded) }
    val currentLink by rememberUpdatedState(pendingLink)
    val actions = NavActions(
        nav,
        today,
        onOnboarded = { skipped ->
            onOnboarded(skipped)
            pastTutorial = true
        },
        pendingLink = { currentLink },
    )
    LaunchedEffect(pendingLink, pastTutorial) {
        val link = pendingLink ?: return@LaunchedEffect
        if (!pastTutorial) return@LaunchedEffect
        actions.openLink(link)
        onLinkOpened(link.link)
    }
    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { fadeIn(motion.enter(BonedleMotion.SCREEN)) },
        exitTransition = { fadeOut(motion.exit(BonedleMotion.SCREEN)) },
    ) {
        composable<HomeRoute> {
            HomeDestination(
                onOpenDaily = { id, status -> actions.openDaily(id, status, GameSource.HOME) },
                onPlayUnlimited = actions::playUnlimited,
                onSettings = { nav.navigate(SettingsRoute) },
                onHowToPlay = actions::howToPlay,
                onTab = actions::tab,
            )
        }
        composable<StatsRoute> { StatsDestination(onTab = actions::tab) }
        composable<ArchiveRoute> {
            ArchiveDestination(
                onOpen = { id, status -> actions.openDaily(id, status, GameSource.ARCHIVE) },
                onTab = actions::tab,
                onLocked = { nav.navigate(PlusRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsDestination(
                onBack = { nav.popBackStack() },
                onHowToPlay = actions::howToPlay,
                onCredits = { nav.navigate(CreditsRoute) },
                onPlus = { nav.navigate(PlusRoute) },
            )
        }
        composable<CreditsRoute> { CreditsScreen(onBack = { nav.popBackStack() }) }
        composable<PlayRoute> { entry ->
            val route = entry.toRoute<PlayRoute>()
            PlayDestination(
                onBack = { if (!nav.popBackStack()) onExit() },
                onSolved = { id ->
                    nav.navigate(SolvedRoute(id.key, fresh = true)) { popUpTo<PlayRoute> { inclusive = true } }
                },
                onTutorialNext = { step -> actions.tutorialNext(step, route.source) },
                onSkipTutorial = { actions.finishTutorial(route.source, skipped = true) },
                onPlus = { nav.navigate(PlusRoute) },
            )
        }
        composable<PlusRoute>(
            enterTransition = { slideInVertically(motion.enter(BonedleMotion.SCREEN)) { it } + fadeIn(motion.enter(BonedleMotion.SCREEN)) },
            exitTransition = { slideOutVertically(motion.exit(BonedleMotion.SCREEN)) { it } + fadeOut(motion.exit(BonedleMotion.SCREEN)) },
        ) {
            PlusDestination(onClose = { nav.popBackStack() })
        }
        composable<SolvedRoute>(
            // The Solved screen slides up over the board (spec 4.5).
            enterTransition = { slideInVertically(motion.enter(BonedleMotion.SCREEN)) { it } + fadeIn(motion.enter(BonedleMotion.SCREEN)) },
            exitTransition = { slideOutVertically(motion.exit(BonedleMotion.SCREEN)) { it } + fadeOut(motion.exit(BonedleMotion.SCREEN)) },
        ) {
            SolvedDestination(onHome = actions::home, onNext = actions::nextUnlimited)
        }
    }
}

/** Navigation moves shared by several screens. */
private class NavActions(
    private val nav: NavHostController,
    private val today: () -> CalendarDate,
    private val onOnboarded: (skipped: Boolean) -> Unit,
    private val pendingLink: () -> PendingLink?,
) {
    fun tab(tab: Tab) {
        val route: Any = when (tab) {
            Tab.Home -> HomeRoute
            Tab.Stats -> StatsRoute
            Tab.Archive -> ArchiveRoute
        }
        nav.navigate(route) {
            popUpTo<HomeRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** A solved daily opens its result; anything else opens the board. */
    fun openDaily(id: GameId.Daily, status: BoardStatus, source: GameSource) {
        if (status is BoardStatus.Solved) {
            nav.navigate(SolvedRoute(id.key))
        } else {
            nav.navigate(PlayRoute(gameKey = id.key, difficulty = id.difficulty.code, source = source.code))
        }
    }

    fun playUnlimited(difficulty: Difficulty, resume: GameId.Unlimited?) {
        nav.navigate(PlayRoute(gameKey = resume?.key, difficulty = difficulty.code, source = GameSource.HOME.code))
    }

    fun nextUnlimited(difficulty: Difficulty) {
        nav.navigate(PlayRoute(gameKey = null, difficulty = difficulty.code, source = GameSource.HOME.code)) {
            popUpTo<SolvedRoute> { inclusive = true }
        }
    }

    fun howToPlay() {
        nav.navigate(PlayRoute(GameId.Tutorial(0).key, source = TutorialSource.HOW_TO))
    }

    fun tutorialNext(step: Int, source: String) {
        if (step + 1 < TutorialBoards.STEPS) {
            nav.navigate(PlayRoute(GameId.Tutorial(step + 1).key, source = source)) { popUpTo<PlayRoute> { inclusive = true } }
        } else {
            finishTutorial(source, skipped = false)
        }
    }

    /**
     * First launch: Home, with today's Easy daily on top, or the friend's board when a challenge
     * link is waiting (it opens as soon as [onOnboarded] lands). A replay just returns.
     */
    fun finishTutorial(source: String, skipped: Boolean) {
        if (source != TutorialSource.ONBOARDING) {
            if (!nav.popBackStack()) home()
            return
        }
        nav.navigate(HomeRoute) { popUpTo(nav.graph.id) { inclusive = true } }
        if (pendingLink() == null) {
            val daily = GameId.Daily(DailySchedule.numberOn(today()), Difficulty.EASY)
            nav.navigate(PlayRoute(gameKey = daily.key, difficulty = daily.difficulty.code, source = GameSource.HOME.code))
        }
        onOnboarded(skipped)
    }

    /** A challenge link: its board over Home, or its result if the player already solved it. */
    fun openLink(pending: PendingLink) {
        home()
        val id = pending.link.id
        if (pending.solved) {
            nav.navigate(SolvedRoute(id.key))
        } else {
            nav.navigate(PlayRoute(gameKey = id.key, difficulty = id.difficulty.code, source = GameSource.LINK.code))
        }
    }

    /** Back to Home, which may not be on the back stack (for example after the tutorial). */
    fun home() {
        if (!nav.popBackStack(HomeRoute, inclusive = false)) {
            nav.navigate(HomeRoute) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }
}
