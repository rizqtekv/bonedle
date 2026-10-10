import java.net.URI

// Declares every plugin once (apply false) so all modules share one classpath and version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// The website for GitHub Pages (web/README.md): web/public with the site's base path filled in
// (`@BASE@`, from gradle.properties `bonedle.siteUrl`) plus the daily board images.
//   ./gradlew assembleSite   ->   build/site
val siteUrl: String = providers.gradleProperty("bonedle.siteUrl").get().trimEnd('/')
val siteBase: String = URI(siteUrl).path.orEmpty().trimEnd('/')

tasks.register<Sync>("assembleSite") {
    description = "Builds the website into build/site, ready to publish."
    group = "distribution"
    dependsOn(":engine:generateWebBoards")
    val base = siteBase
    from("web/public") {
        filesMatching(listOf("**/*.html", "**/*.css", "**/*.txt")) {
            filter { line: String -> line.replace("@BASE@", base) }
        }
    }
    from(layout.buildDirectory.dir("site-boards")) { into("img") }
    // At an account's root the site itself must serve the asset links.
    if (base.isEmpty()) from("web/root-site") { include(".well-known/**") }
    into(layout.buildDirectory.dir("site"))
}

