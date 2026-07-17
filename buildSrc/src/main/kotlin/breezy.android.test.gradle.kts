import breezy.buildlogic.AndroidConfig
import com.android.build.api.dsl.TestExtension

plugins {
    id("com.android.test")

    id("breezy.code.lint")
}

configure<TestExtension> {
    compileSdk = AndroidConfig.COMPILE_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
    }

    compileOptions {
        sourceCompatibility = AndroidConfig.JavaVersion
        targetCompatibility = AndroidConfig.JavaVersion
    }
}
