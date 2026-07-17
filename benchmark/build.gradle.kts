plugins {
    id("breezy.android.test")
}

android {
    namespace = "com.liveweatherwallpaperapp.benchmark"

    // Mirrors :app's own flavor dimension -- a com.android.test module must declare the same
    // flavors as its targetProjectPath so AGP can match variants (e.g. freenetBenchmark).
    flavorDimensions += "default"
    productFlavors {
        create("basic") {
            dimension = "default"
        }
        create("freenet") {
            dimension = "default"
        }
    }

    // Own build type matching :app's "benchmark" one by name (see app/build.gradle.kts) -- a
    // release-shaped, non-debuggable, profileable build, since a debuggable target APK gives
    // meaningless timings (no R8 shrinking/optimization, debug JIT mode).
    buildTypes {
        create("benchmark") {
            isDebuggable = true // the *test* APK itself still needs to be debuggable to attach
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Only the "benchmark" build type variant is meaningful here (debug/release would either be
// unoptimized or unprofileable) -- disable every other variant so `./gradlew :benchmark:assemble`
// and IDE sync don't try to build them against a matching :app variant that isn't measurable.
androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.enable = variant.buildType == "benchmark"
    }
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
