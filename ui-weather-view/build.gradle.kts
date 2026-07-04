import com.android.build.api.dsl.LibraryExtension

plugins {
    id("breezy.library")
}

configure<LibraryExtension> {
    namespace = "com.liveweatherwallpaperapp.ui.theme.weatherView"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(libs.core.ktx)
}
