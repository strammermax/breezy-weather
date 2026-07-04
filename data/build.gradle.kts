import com.android.build.api.dsl.LibraryExtension

plugins {
    id("breezy.library")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
}

configure<LibraryExtension> {
    namespace = "livewallpaperweather.data"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    sqldelight {
        databases {
            create("Database") {
                packageName.set("livewallpaperweather.data")
                dialect(libs.sqldelight.dialects.sql)
                schemaOutputDirectory.set(project.file("./src/main/sqldelight"))
            }
        }
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.weatherUnit)

    api(libs.bundles.sqldelight)
}
