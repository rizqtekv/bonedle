# Art

Everything visual that is not drawn by the app's own code: the icon and the Play Store listing.

| File | What | Upload to |
| --- | --- | --- |
| `store/icon-512.png` | App icon, 512 x 512, full-bleed (Play applies its own mask) | Play Console > Main store listing > App icon |
| `store/feature-graphic.png` | Feature graphic, 1024 x 500 | Main store listing > Feature graphic |
| `store/screenshots/01-…08-*.png` | Eight phone screenshots, 1080 x 1920, one benefit headline each | Main store listing > Phone screenshots (in order) |

## Regenerating

The icon is described once, in `icon.py`, which writes the master `icon.svg`, the adaptive
launcher layers (`ic_launcher_background/foreground/monochrome.xml` in `app/src/main/res/drawable`)
and the layers the feature graphic uses. After changing it:

```
python art/icon.py
chrome --headless=new --screenshot=art/store/icon-512.png --window-size=512,512 art/play-icon.html
chrome --headless=new --allow-file-access-from-files --screenshot=art/store/feature-graphic.png --window-size=1024,500 art/store/feature-graphic.html
```

The screenshots are real app screens in staged states (`StoreScreensTest`), framed with a
headline by `store/compose.py`:

```
./gradlew :app:recordRoborazziDebug --tests "*StoreScreensTest*"
python art/store/compose.py
```

Headlines live in `store/compose.py`; each must describe something the app really does.
