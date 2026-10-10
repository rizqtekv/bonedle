package app.bonedle.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import app.bonedle.ui.theme.Sizes

/**
 * Custom stroke icons (spec 4.6): 24dp grid, 1.9dp strokes, rounded caps and joins. Drawn in black
 * and tinted by `Icon`. A path ending in a zero-length segment draws a round dot.
 */
object BonedleIcons {
    val Back = icon("back", "M15 5L8 12L15 19")
    val Close = icon("close", "M6 6L18 18M18 6L6 18")
    val ChevronRight = icon("chevron_right", "M10 6L16 12L10 18")
    val Settings = icon(
        "settings",
        "M4 7H7M11 7H20M4 17H13M17 17H20",
        circle(9f, 7f, 2f),
        circle(15f, 17f, 2f),
    )
    val Home = icon("home", "M4 10.5L12 4L20 10.5V19A1 1 0 0 1 19 20H15V14H9V20H5A1 1 0 0 1 4 19Z")
    val Stats = icon("stats", "M5 20V13M12 20V5M19 20V10")
    val Archive = icon("archive", "M6.5 5H17.5A2.5 2.5 0 0 1 20 7.5V17.5A2.5 2.5 0 0 1 17.5 20H6.5A2.5 2.5 0 0 1 4 17.5V7.5A2.5 2.5 0 0 1 6.5 5ZM4 10H20M8 3V7M16 3V7")
    val Undo = icon("undo", "M9 6L5 10L9 14M5 10H14.5A4.5 4.5 0 0 1 14.5 19H10")
    val Rotate = icon("rotate", "M19 12A7 7 0 1 1 16.95 7.05M17 3V7.05H13")
    val Restart = icon("restart", "M5 12A7 7 0 1 0 7.05 7.05M7 3V7.05H11")
    val Hint = icon(
        "hint",
        "M9 18H15M10 21H14M12 3A6 6 0 0 0 8 13.5C8.8 14.3 9 15 9 15.5H15C15 15 15.2 14.3 16 13.5A6 6 0 0 0 12 3Z",
    )
    val Share = icon("share", "M12 4V15M8 8L12 4L16 8M6 12V19A1 1 0 0 0 7 20H17A1 1 0 0 0 18 19V12")
    val Play = icon("play", "M8 5.5V18.5L18.5 12Z")
    val Timer = icon("timer", "M12 13V9.5M10 3H14", circle(12f, 13f, 7f))
    val Check = icon("check", "M5 12.5L10 17.5L19 7")
    val Lock = icon("lock", "M7 11H17A2 2 0 0 1 19 13V18A2 2 0 0 1 17 20H7A2 2 0 0 1 5 18V13A2 2 0 0 1 7 11ZM8 11V8A4 4 0 0 1 16 8V11")
    val Flame = icon(
        "flame",
        "M12 21C8.5 21 6.5 18.5 6.5 15.5C6.5 11.5 10 10 10.5 4.5C13.5 7 17.5 10.5 17.5 15.5C17.5 18.5 15.5 21 12 21ZM12 21C10.6 21 9.8 20 9.8 18.7C9.8 17 11.2 16.3 11.5 14.2C12.8 15.3 14.2 16.6 14.2 18.7C14.2 20 13.4 21 12 21Z",
    )
    val Help = icon("help", "M9.5 9.5A2.5 2.5 0 1 1 13.2 11.7C12.4 12.2 12 12.8 12 13.6M12 17V17.01", circle(12f, 12f, 9f))
    val Info = icon("info", "M12 11V16.5M12 7.8V7.81", circle(12f, 12f, 9f))
    val Friends = icon(
        "friends",
        "M3.5 19C3.5 15.5 6 14 9 14C12 14 14.5 15.5 14.5 19M16.5 14C18.8 14 20.5 15.3 20.5 18",
        circle(9f, 8f, 3.2f),
        circle(16.5f, 9f, 2.5f),
    )
    val Sound = icon("sound", "M4 9.5H7.5L12 5.5V18.5L7.5 14.5H4ZM15.5 9A4 4 0 0 1 15.5 15M18 6.5A7.5 7.5 0 0 1 18 17.5")
    val SoundOff = icon("sound_off", "M4 9.5H7.5L12 5.5V18.5L7.5 14.5H4ZM16 9.5L21 14.5M21 9.5L16 14.5")
    val Haptics = icon("haptics", "M10 4H14A2 2 0 0 1 16 6V18A2 2 0 0 1 14 20H10A2 2 0 0 1 8 18V6A2 2 0 0 1 10 4ZM4.5 9V15M19.5 9V15")
    val Bell = icon("bell", "M6.5 16.5V11A5.5 5.5 0 0 1 17.5 11V16.5L19 18H5ZM10 20.5A2 2 0 0 0 14 20.5")
    val Eye = icon("eye", "M3 12C5.5 7.5 8.5 6 12 6C15.5 6 18.5 7.5 21 12C18.5 16.5 15.5 18 12 18C8.5 18 5.5 16.5 3 12Z", circle(12f, 12f, 3f))
    val EyeOff = icon(
        "eye_off",
        "M3 12C5.5 7.5 8.5 6 12 6C15.5 6 18.5 7.5 21 12C18.5 16.5 15.5 18 12 18C8.5 18 5.5 16.5 3 12ZM4 4L20 20",
        circle(12f, 12f, 3f),
    )
    val Crown = icon("crown", "M4 8L8.5 12L12 5.5L15.5 12L20 8L18.5 18H5.5Z")
    val Video = icon("video", "M6 5H18A3 3 0 0 1 21 8V16A3 3 0 0 1 18 19H6A3 3 0 0 1 3 16V8A3 3 0 0 1 6 5ZM10.5 9.5V14.5L14.5 12Z")
    val Theme = icon("theme", "M20 14.5A8 8 0 1 1 9.5 4A6.5 6.5 0 0 0 20 14.5Z")
    val Shield = icon("shield", "M12 3L19 6V11.5C19 16 16 19.2 12 21C8 19.2 5 16 5 11.5V6Z")
    val Snowflake = icon("streak_freeze", "M12 3V21M4.2 7.5L19.8 16.5M4.2 16.5L19.8 7.5M9.5 4.5L12 6L14.5 4.5M9.5 19.5L12 18L14.5 19.5")
    val Restore = icon("restore", "M20 12A8 8 0 1 1 17.66 6.34M18 3V6.5H14.5M12 8V12L14.5 14.5")
    val Copy = icon("copy", "M10 8H18A2 2 0 0 1 20 10V18A2 2 0 0 1 18 20H10A2 2 0 0 1 8 18V10A2 2 0 0 1 10 8ZM16 8V6A2 2 0 0 0 14 4H6A2 2 0 0 0 4 6V14A2 2 0 0 0 6 16H8")

    /** Every icon, for the gallery. */
    val all: List<ImageVector> = listOf(
        Back, Close, ChevronRight, Settings, Home, Stats, Archive, Undo, Rotate, Restart, Hint, Share, Play,
        Timer, Check, Lock, Flame, Help, Info, Friends, Sound, SoundOff, Haptics, Bell, Eye, EyeOff, Crown, Video,
        Theme, Shield, Snowflake, Restore, Copy,
    )

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}A$r $r 0 1 0 ${cx + r} ${cy}A$r $r 0 1 0 ${cx - r} ${cy}Z"

    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (d in paths) {
            builder.addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = Sizes.ICON_STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
