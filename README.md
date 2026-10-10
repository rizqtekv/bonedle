# Bonedle: Domino Logic Puzzle

Android puzzle game. Place every domino so each coloured region's rule holds; every board has
exactly one solution and can be solved by logic alone.

## Modules

| Module | Contents |
|---|---|
| `:engine` | Pure Kotlin (Multiplatform, JVM target today; all code in `commonMain` so a JS target can be added for the web challenge page). Model, PRNG, exact solver, human-style solver and grader, generator, daily schedule, codec, fallback bank. |
| `:app` | Android app: Compose UI, Room/DataStore persistence, sharing, App Links, reminders, analytics. |
| `web/` | Website on GitHub Pages: challenge page, privacy policy, App Link verification (see `web/README.md`). |

## Engine at a glance

- `PuzzleGenerator.generate(seed, difficulty)` — deterministic: the same seed gives a
  byte-identical board on every platform.
- `DailySchedule.puzzle(number, difficulty)` — the global daily boards (Bonedle #1 is
  `DailySchedule.LAUNCH_DATE`).
- `ExactSolver.solve(...)` — counts solutions (capped), used for uniqueness.
- `HumanSolver.grade(...)` / `nextDeduction(...)` — difficulty grading and hints.
- `RuleCheck` — region status and solved detection for the player's board.
- `PuzzleCodec` / `PuzzleBank` — canonical binary/base64url encoding and the bundled bank.

## Commands

```
./gradlew :engine:jvmTest          # unit, golden, differential, 300-board property sample, 730-day dailies
./gradlew :engine:propertyTest     # spec suite: 10,000 boards per difficulty (writes build/reports/engine/)
./gradlew :engine:performanceTest  # generation-time budgets, measured alone
./gradlew :engine:generateBank     # regenerate the 200-per-difficulty fallback bank
```

`-Pbonedle.propertyCount=N` changes the property sample size; `-Pbonedle.jfr=<file>` records a
Flight Recorder profile of `jvmTest`.

## App build properties

Set in `gradle.properties` or passed with `-P`:

| Property | Default | Use |
|---|---|---|
| `bonedle.siteUrl` | `https://rizqtekv.github.io/bonedle` | The website: challenge links, App Links and the privacy policy. |
| `bonedle.firebase.appId`, `.apiKey`, `.projectId`, `.senderId` | empty | Firebase project keys. Without them analytics is off (debug builds log events to Logcat). |
| `bonedle.certSha256` | | Signing-key fingerprints for `./gradlew :app:writeAssetLinks`. |
| `bonedle.admob.appId`, `.rewardedHint`, `.rewardedFreeze`, `.interstitial` | empty | AdMob App ID and ad units ("Extra hint", "Streak saver", "Between Unlimited puzzles"). Debug builds always use Google's test ads; a release build missing any of them makes no ad requests. |
| `bonedle.admob.publisherId` | | `pub-…`, for `./gradlew :app:writeAppAdsTxt`. |
| `bonedle.billing.publicKey` | empty | Play licensing key (Monetize with Play > Monetization setup); when set, purchase signatures are verified. Bonedle Plus is the one-time product `bonedle_plus`. |
| `bonedle.signing.storeFile`, `.storePassword`, `.keyAlias`, `.keyPassword` | | Release signing (upload key). Keep these in `~/.gradle/gradle.properties`, never in the repository. |
| `bonedle.ads.debugNow`, `bonedle.ump.debugEea`, `bonedle.ump.testDevice` | false | Debug builds only: test ads from the first session; show the consent form as if in the EEA. |

```
./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug   # app tests and screenshot checks
./gradlew :app:recordRoborazziDebug                          # re-record screenshots after a UI change
./gradlew assembleSite                                       # the website, with board images, into build/site
```
