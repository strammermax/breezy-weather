import com.android.build.api.dsl.LibraryExtension

plugins {
    id("breezy.library")
}

configure<LibraryExtension> {
    namespace = "com.wolkentypes.app.clouds"
}

dependencies {
    implementation(libs.core.ktx)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform)
}
