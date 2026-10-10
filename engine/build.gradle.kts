plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)
    // Only the JVM target ships in v1. All engine code lives in commonMain so a js() target
    // can be added for the web challenge page without touching the sources.
    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Test tuning knobs: any -Pbonedle.* Gradle property is forwarded to the test JVM.
val engineTestSettings: Map<String, Any> = buildMap {
    put("bonedle.propertyCount", "300")
    put("bonedle.reportDir", layout.buildDirectory.dir("reports/engine").get().asFile.absolutePath)
    providers.gradlePropertiesPrefixedBy("bonedle.").get().forEach { (k, v) -> put(k, v) }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    filter { excludeTestsMatching("app.bonedle.engine.PerformanceTest") }
    maxHeapSize = "2g"
    // -Pbonedle.jfr=<file.jfr> records a Flight Recorder profile of the test JVM.
    providers.gradleProperty("bonedle.jfr").orNull?.let { jfr ->
        jvmArgs("-XX:StartFlightRecording=filename=$jfr,settings=profile")
    }
    engineTestSettings.forEach { (k, v) -> systemProperty(k, v.toString()) }
    testLogging {
        events("failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Fallback bank (spec 7.3): 200 validated boards per difficulty.
//   ./gradlew :engine:generateBank [-Pbonedle.bankDir=<dir>]
tasks.register<JavaExec>("generateBank") {
    description = "Generates and validates the bundled fallback puzzle bank."
    group = "build"
    val testCompilation = kotlin.jvm().compilations.getByName("test")
    classpath = files(testCompilation.output.allOutputs, testCompilation.runtimeDependencyFiles)
    mainClass.set("app.bonedle.engine.BankToolKt")
    args(providers.gradleProperty("bonedle.bankDir").getOrElse(layout.buildDirectory.dir("bank").get().asFile.absolutePath))
    dependsOn("jvmTestClasses")
}

// Board images for the challenge web page: every daily through tomorrow (UTC), as SVG, into
// build/site-boards (the root assembleSite task publishes them as img/).
//   ./gradlew :engine:generateWebBoards [-Pbonedle.webDir=<dir>] [-Pbonedle.webThrough=YYYY-MM-DD]
tasks.register<JavaExec>("generateWebBoards") {
    description = "Renders daily board silhouettes for the website."
    group = "build"
    val testCompilation = kotlin.jvm().compilations.getByName("test")
    classpath = files(testCompilation.output.allOutputs, testCompilation.runtimeDependencyFiles)
    mainClass.set("app.bonedle.engine.web.WebBoardsToolKt")
    args(
        providers.gradleProperty("bonedle.webDir").getOrElse(rootProject.layout.buildDirectory.dir("site-boards").get().asFile.absolutePath),
        providers.gradleProperty("bonedle.webThrough").getOrElse(""),
    )
    dependsOn("jvmTestClasses")
}

// Generation-time budgets (spec 7.3), measured alone so other suites cannot skew them.
//   ./gradlew :engine:performanceTest
tasks.register<Test>("performanceTest") {
    description = "Checks mean generation time per difficulty against the device budgets."
    group = "verification"
    val jvmTest = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath
    useJUnitPlatform()
    filter { includeTestsMatching("app.bonedle.engine.PerformanceTest") }
    outputs.upToDateWhen { false }
    testLogging {
        events("failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Full property suite from the spec: 10,000 boards per difficulty. Run explicitly:
//   ./gradlew :engine:propertyTest
tasks.register<Test>("propertyTest") {
    description = "Generates 10,000 boards per difficulty and validates every invariant."
    group = "verification"
    val jvmTest = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath
    useJUnitPlatform()
    maxHeapSize = "4g"
    filter { includeTestsMatching("app.bonedle.engine.PropertyTest") }
    engineTestSettings.forEach { (k, v) -> systemProperty(k, v.toString()) }
    systemProperty("bonedle.propertyCount", providers.gradleProperty("bonedle.propertyCount").getOrElse("10000"))
    outputs.upToDateWhen { false }
    testLogging {
        events("failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
