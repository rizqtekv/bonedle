// Android application: UI, persistence, ads, billing, analytics, sharing. Game logic lives in :engine.
import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** A `-Pbonedle.<name>=` build property, or [default]. */
fun buildProperty(name: String, default: String = ""): String = providers.gradleProperty("bonedle.$name").getOrElse(default)

// The website (gradle.properties `bonedle.siteUrl`): challenge links, App Links and the privacy
// policy (spec 8.2). Host and optional path, e.g. https://rizqtekv.github.io/bonedle.
val siteUrl = URI(buildProperty("siteUrl").trimEnd('/')).also {
    require(it.scheme == "https" && !it.host.isNullOrEmpty()) { "bonedle.siteUrl must be an https URL: $it" }
}
val linkHost: String = siteUrl.host
val linkPath: String = siteUrl.path.orEmpty().trimEnd('/')

// AdMob (spec 9): App ID and the three ad units, from `bonedle.admob.*`. Debug builds always serve
// Google's test ads, never your own; a release build without every ID makes no ad requests at
// all, so test ads can never ship.
val admobAppId = buildProperty("admob.appId")
val admobUnits = mapOf(
    "AD_UNIT_REWARDED_HINT" to buildProperty("admob.rewardedHint"),
    "AD_UNIT_REWARDED_FREEZE" to buildProperty("admob.rewardedFreeze"),
    "AD_UNIT_INTERSTITIAL" to buildProperty("admob.interstitial"),
)
val admobConfigured = admobAppId.isNotEmpty() && admobUnits.values.all { it.isNotEmpty() }
val testAdUnits = mapOf(
    "AD_UNIT_REWARDED_HINT" to "ca-app-pub-3940256099942544/5224354917",
    "AD_UNIT_REWARDED_FREEZE" to "ca-app-pub-3940256099942544/5224354917",
    "AD_UNIT_INTERSTITIAL" to "ca-app-pub-3940256099942544/1033173712",
)
val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"

// Release signing, from `bonedle.signing.*` (keep them in ~/.gradle/gradle.properties, never in
// the repository). Without them release builds are unsigned.
val signingStore = buildProperty("signing.storeFile")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.bonedle"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.bonedle"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["linkHost"] = linkHost
        manifestPlaceholders["linkPath"] = linkPath
        buildConfigField("String", "LINK_HOST", "\"$linkHost\"")
        buildConfigField("String", "LINK_PATH", "\"$linkPath\"")
        // Firebase project keys (Firebase console > Project settings > Your apps). Without them
        // the build works and analytics stays off; there is no google-services.json.
        buildConfigField("String", "FIREBASE_APP_ID", "\"${buildProperty("firebase.appId")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${buildProperty("firebase.apiKey")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${buildProperty("firebase.projectId")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${buildProperty("firebase.senderId")}\"")
        // The SDK reads the App ID at startup even when ads are off, so it always has one.
        manifestPlaceholders["admobAppId"] = admobAppId.ifEmpty { testAdMobAppId }
        // Play Console > Monetize with Play > Monetization setup > Licensing: verifies purchase
        // signatures on the device. Optional; without it purchases are trusted as Play reports them.
        buildConfigField("String", "BILLING_PUBLIC_KEY", "\"${buildProperty("billing.publicKey")}\"")
        // Debug only: show the consent form as if in the EEA (`-Pbonedle.ump.debugEea=true`); a
        // real phone also needs its hashed ID from Logcat (`-Pbonedle.ump.testDevice=`).
        buildConfigField("boolean", "UMP_DEBUG_EEA", buildProperty("ump.debugEea", "false"))
        buildConfigField("String", "UMP_TEST_DEVICE", "\"${buildProperty("ump.testDevice")}\"")
        // Debug only: allow (test) ads in the first session too (`-Pbonedle.ads.debugNow=true`),
        // to try them without waiting for a second session.
        buildConfigField("boolean", "ADS_DEBUG_NOW", buildProperty("ads.debugNow", "false"))
    }

    signingConfigs {
        if (signingStore.isNotEmpty()) {
            create("release") {
                storeFile = file(signingStore)
                storePassword = buildProperty("signing.storePassword")
                keyAlias = buildProperty("signing.keyAlias")
                keyPassword = buildProperty("signing.keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "ADS_ENABLED", "true")
            testAdUnits.forEach { (name, id) -> buildConfigField("String", name, "\"$id\"") }
        }
        getByName("release") {
            buildConfigField("boolean", "ADS_ENABLED", admobConfigured.toString())
            admobUnits.forEach { (name, id) -> buildConfigField("String", name, "\"$id\"") }
            if (signingStore.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Exported Room schemas are what the migration tests replay. Robolectric reads the tested
    // variant's assets, so they ship in debug builds only (a few KB), never in release.
    sourceSets {
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.maxHeapSize = "3g"
            }
        }
    }
}

// Robolectric runs API 35+ sandboxes only on Java 21; compilation stays on 17.
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    // Robolectric's file-descriptor shadow reaches into JDK internals on Java 21.
    jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Digital Asset Links for App Link verification, written to web/root-site/.well-known (the
// account's root site, where Android looks for them):
//   ./gradlew :app:writeAssetLinks -Pbonedle.certSha256=AA:BB:...[,CC:DD:...]
// Use the SHA-256 of the Play App Signing key (Play Console > App integrity), plus the upload
// or debug key to verify locally installed builds.
tasks.register("writeAssetLinks") {
    description = "Writes web/root-site/.well-known/assetlinks.json for the given signing certificates."
    group = "distribution"
    val fingerprints = buildProperty("certSha256")
    val packageName = "app.bonedle"
    val target = rootProject.layout.projectDirectory.file("web/root-site/.well-known/assetlinks.json")
    outputs.file(target)
    doLast {
        val certs = fingerprints.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val pattern = Regex("^([0-9A-F]{2}:){31}[0-9A-F]{2}$")
        require(certs.isNotEmpty()) { "pass -Pbonedle.certSha256=<SHA-256 fingerprint>[,<another>]" }
        certs.forEach { require(pattern.matches(it)) { "not a SHA-256 fingerprint: $it" } }
        val list = certs.joinToString(",\n") { "        \"$it\"" }
        val json = """
            |[
            |  {
            |    "relation": ["delegate_permission/common.handle_all_urls"],
            |    "target": {
            |      "namespace": "android_app",
            |      "package_name": "$packageName",
            |      "sha256_cert_fingerprints": [
            |$list
            |      ]
            |    }
            |  }
            |]
            |
        """.trimMargin()
        val file = target.asFile
        file.parentFile.mkdirs()
        file.writeText(json)
        println("Wrote ${file.path}")
    }
}

// app-ads.txt for AdMob: authorises Google to sell this app's ad space. It must sit at the root
// of the developer website listed in the Play Store (web/root-site, i.e. rizqtekv.github.io).
//   ./gradlew :app:writeAppAdsTxt   (reads bonedle.admob.publisherId, e.g. pub-1234567890123456)
tasks.register("writeAppAdsTxt") {
    description = "Writes web/root-site/app-ads.txt for the AdMob publisher ID."
    group = "distribution"
    val publisherId = buildProperty("admob.publisherId")
    val target = rootProject.layout.projectDirectory.file("web/root-site/app-ads.txt")
    outputs.file(target)
    doLast {
        require(Regex("^pub-[0-9]{16}$").matches(publisherId)) { "set bonedle.admob.publisherId=pub-<16 digits>" }
        val file = target.asFile
        file.writeText("google.com, $publisherId, DIRECT, f08c47fec0942fa0\n")
        println("Wrote ${file.path}")
    }
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.play.review)
    implementation(libs.installreferrer)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing.ktx)
    implementation(libs.androidx.lifecycle.process)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(composeBom)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
}
