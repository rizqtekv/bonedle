package app.bonedle.gallery

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.ThemeMode

@Preview(name = "Gallery - dark", heightDp = 3200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GalleryDarkPreview() {
    BonedleTheme(mode = ThemeMode.DARK, reducedMotion = false) { DesignGallery(ThemeMode.DARK) {} }
}

@Preview(name = "Gallery - light", heightDp = 3200)
@Composable
private fun GalleryLightPreview() {
    BonedleTheme(mode = ThemeMode.LIGHT, reducedMotion = false) { DesignGallery(ThemeMode.LIGHT) {} }
}

@Preview(name = "Gallery - 200% font", heightDp = 4400, fontScale = 2f)
@Composable
private fun GalleryLargeFontPreview() {
    BonedleTheme(mode = ThemeMode.DARK, reducedMotion = false) { DesignGallery(ThemeMode.DARK) {} }
}

@Preview(name = "Gallery - API 29 (no blur)", heightDp = 3200, apiLevel = 29)
@Composable
private fun GalleryApi29Preview() {
    BonedleTheme(mode = ThemeMode.DARK, reducedMotion = false) { DesignGallery(ThemeMode.DARK) {} }
}
