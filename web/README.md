# Bonedle website

Static site on GitHub Pages at `bonedle.siteUrl` (gradle.properties), today
`https://rizqtekv.github.io/bonedle`. The app's challenge links, verified App Links and privacy
policy link are all built from the same setting.

| Address | What |
| --- | --- |
| `/bonedle/` | Landing page with the Play Store button. |
| `/bonedle/d/<number>/<difficulty>`, `/bonedle/u/<seed>/<difficulty>` | Challenge page. GitHub Pages has no rewrites, so these land on `404.html`, which reads the board from the address: the board image for dailies, the friend's time from `?t=&h=`, and a Play Store button that carries the board in the install referrer. With the app installed, App Links open the board in the app instead. |
| `/bonedle/privacy.html` | Privacy policy (linked from the app's Settings). |
| `/bonedle/img/d/<number>-<difficulty>.svg` | Daily board silhouettes, generated. |
| `/.well-known/assetlinks.json` | App Link verification. Android only looks at the root of the host, so this lives in the separate `rizqtekv.github.io` repository (`web/root-site`). |

Pages use `@BASE@` for the site's path; `./gradlew assembleSite` fills it in, adds the board
images and writes the finished site to `build/site`.

## Publishing

The `Website` workflow (`.github/workflows/site.yml`) builds and deploys on every push to `main`
and every day at 23:30 UTC. The daily run writes board images through tomorrow (UTC), never later,
so future boards are not published early. One-time setup: repository Settings > Pages > Source:
GitHub Actions.

## App Link verification (one time per signing key)

1. Get the SHA-256 fingerprints: Play Console > Test and release > App integrity > App signing key
   certificate (and the upload key, to verify locally installed release builds).
2. `./gradlew :app:writeAssetLinks -Pbonedle.certSha256=<SHA-256>[,<another>]` writes
   `web/root-site/.well-known/assetlinks.json`.
3. Publish `web/root-site` as the `rizqtekv/rizqtekv.github.io` repository (Pages from the `main`
   branch). Its `.nojekyll` keeps `.well-known` from being dropped, and its `index.html` forwards
   visitors to `/bonedle/`.

If the site later moves to its own domain, change `bonedle.siteUrl`; at a host's root the asset
links are published with the site itself.

The challenge page mirrors `app.bonedle.domain.Links` and `app.bonedle.domain.Referrer`; change
them together.
