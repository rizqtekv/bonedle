package app.bonedle.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of every design-gallery section in the four variants spec 13 requires: dark,
 * light, font scale 200 %, and API 29 (no blur). Record with `./gradlew :app:recordRoborazziDebug`,
 * check with `:app:verifyRoborazziDebug`.
 */
abstract class GalleryScreenshots(private val section: Int, private val mode: ThemeMode, private val fontScale: Float, private val variant: String) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun section() {
        val (name, content) = GallerySections[section]
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BonedleTheme(mode = mode, reducedMotion = true) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .testTag(TAG),
                    ) { content() }
                }
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("src/test/screenshots/$variant/${section.toString().padStart(2, '0')}-$name.png")
    }

    companion object {
        const val TAG = "gallery-section"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun sections(): List<Array<Any>> = GallerySections.indices.map { arrayOf(it) }
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1800dp-xhdpi")
class GalleryDarkScreenshotTest(section: Int) : GalleryScreenshots(section, ThemeMode.DARK, 1f, "dark")

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1800dp-xhdpi")
class GalleryLightScreenshotTest(section: Int) : GalleryScreenshots(section, ThemeMode.LIGHT, 1f, "light")

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h3200dp-xhdpi")
class GalleryLargeFontScreenshotTest(section: Int) : GalleryScreenshots(section, ThemeMode.DARK, 2f, "font200")

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "w411dp-h1800dp-xhdpi")
class GalleryApi29ScreenshotTest(section: Int) : GalleryScreenshots(section, ThemeMode.DARK, 1f, "api29")
