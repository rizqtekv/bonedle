package app.bonedle.data.game

import app.bonedle.engine.model.Difficulty

/**
 * Identifies one board: a daily by number, or an Unlimited board by seed. [key] is the stable
 * string used for saves, results and navigation.
 */
sealed interface GameId {
    val difficulty: Difficulty
    val key: String

    data class Daily(val number: Int, override val difficulty: Difficulty) : GameId {
        override val key: String get() = "d:$number:${difficulty.code}"
    }

    data class Unlimited(val seed: Long, override val difficulty: Difficulty) : GameId {
        override val key: String get() = "u:$seed:${difficulty.code}"
    }

    /** A first-launch tutorial board; never saved or recorded. */
    data class Tutorial(val step: Int) : GameId {
        override val difficulty: Difficulty get() = Difficulty.EASY
        override val key: String get() = "t:$step:easy"
    }

    companion object {
        /** Inverse of [key]; null if malformed. */
        fun parse(key: String): GameId? {
            val parts = key.split(':')
            if (parts.size != 3) return null
            val difficulty = Difficulty.fromCode(parts[2]) ?: return null
            return when (parts[0]) {
                "d" -> parts[1].toIntOrNull()?.takeIf { it >= 1 }?.let { Daily(it, difficulty) }
                "u" -> parts[1].toLongOrNull()?.let { Unlimited(it, difficulty) }
                "t" -> parts[1].toIntOrNull()?.takeIf { it >= 0 }?.let { Tutorial(it) }
                else -> null
            }
        }
    }
}

/** Where the player opened a board from (analytics `puzzle_start.source`). */
enum class GameSource(val code: String) {
    HOME("home"),
    LINK("link"),
    ARCHIVE("archive"),
    ;

    companion object {
        fun fromCode(code: String): GameSource = entries.firstOrNull { it.code == code } ?: HOME
    }
}
