package app.bonedle.engine.model

/** Puzzle difficulty. [code] is the stable identifier used in links, seeds and storage. */
enum class Difficulty(val code: String) {
    EASY("easy"),
    MEDIUM("medium"),
    HARD("hard"),
    EXPERT("expert"),
    ;

    companion object {
        /** Difficulties offered as daily boards. */
        val DAILY: List<Difficulty> = listOf(EASY, MEDIUM, HARD)

        /** Returns the difficulty whose [code] equals [code], or null. */
        fun fromCode(code: String): Difficulty? = entries.firstOrNull { it.code == code }
    }
}
