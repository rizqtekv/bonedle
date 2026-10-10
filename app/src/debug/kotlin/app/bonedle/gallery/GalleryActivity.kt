package app.bonedle.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.ThemeMode

/** Debug-only launcher for the design gallery. */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            var mode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
            BonedleTheme(mode = mode) {
                DesignGallery(mode = mode, onModeChange = { mode = it })
            }
        }
    }
}
