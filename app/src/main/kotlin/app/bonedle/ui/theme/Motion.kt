package app.bonedle.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion tokens (spec 4.5). When the system "remove animations" setting is on ([reduced]), every
 * spec collapses to a short fade-friendly tween or an immediate snap, so callers never need to
 * branch themselves.
 */
@Immutable
class BonedleMotion(val reduced: Boolean) {
    /** Enter / settle: emphasised decelerate. */
    fun <T> enter(durationMs: Int = COMPONENT): FiniteAnimationSpec<T> =
        if (reduced) tween(REDUCED_MS) else tween(durationMs, easing = EmphasizedDecelerate)

    /** Exit: emphasised accelerate. */
    fun <T> exit(durationMs: Int = COMPONENT): FiniteAnimationSpec<T> =
        if (reduced) tween(REDUCED_MS) else tween(durationMs, easing = EmphasizedAccelerate)

    /** Domino pick-up: scale to [PICK_UP_SCALE] with a ~120ms spring, damping 0.7. */
    fun <T> pickUp(): AnimationSpec<T> = if (reduced) snap() else spring(dampingRatio = 0.7f, stiffness = PICK_UP_STIFFNESS)

    /** Snap into place: medium stiffness, damping 0.75. */
    fun <T> snapIntoPlace(): AnimationSpec<T> = if (reduced) snap() else spring(dampingRatio = 0.75f, stiffness = SNAP_STIFFNESS)

    /** Invalid drop: three horizontal cycles of ±[SHAKE_AMPLITUDE_DP] dp over 240ms (offset in dp). */
    fun shake(): AnimationSpec<Float> = if (reduced) {
        snap()
    } else {
        keyframes {
            durationMillis = SHAKE_MS
            val step = SHAKE_MS / 6
            for (i in 0 until 6) {
                val target = if (i % 2 == 0) SHAKE_AMPLITUDE_DP else -SHAKE_AMPLITUDE_DP
                target at step * i + step / 2 using LinearEasing
            }
            0f at SHAKE_MS
        }
    }

    /** Region satisfied: outline scale 1.0 → 1.03 → 1.0 over 300ms. */
    fun pulse(): AnimationSpec<Float> = if (reduced) snap() else pulseSpec()

    /** Puzzle solved: accent sweep across the board. */
    fun <T> solvedSweep(): AnimationSpec<T> = if (reduced) tween(REDUCED_MS) else tween(SOLVED_SWEEP_MS, easing = EmphasizedDecelerate)

    companion object {
        const val MICRO = 150
        const val COMPONENT = 250
        const val SCREEN = 350
        const val REDUCED_MS = 100
        const val SHAKE_MS = 240
        const val SHAKE_AMPLITUDE_DP = 8f
        const val PULSE_MS = 300
        const val PULSE_SCALE = 1.03f
        const val PICK_UP_SCALE = 1.06f
        const val SOLVED_SWEEP_MS = 600

        /** Settles in ~120ms at damping 0.7. */
        const val PICK_UP_STIFFNESS = 1_500f
        const val SNAP_STIFFNESS = 1_500f

        val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

        private fun pulseSpec(): KeyframesSpec<Float> = keyframes {
            durationMillis = PULSE_MS
            PULSE_SCALE at PULSE_MS / 2 using EmphasizedDecelerate
            1f at PULSE_MS using EmphasizedAccelerate
        }
    }
}

/** Provided by [BonedleTheme]. */
val LocalBonedleMotion = staticCompositionLocalOf { BonedleMotion(reduced = false) }
