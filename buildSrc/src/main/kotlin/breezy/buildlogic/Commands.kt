package breezy.buildlogic

import org.gradle.api.Project

// Git is needed in your system PATH for these commands to work.
// If it's not installed, you can return a random value as a workaround
fun Project.getCommitCount(): String {
    return runCommand("git rev-list --count HEAD")
    // return "1"
}

fun Project.getGitSha(): String {
    return runCommand("git rev-parse --short=8 HEAD")
    // return "1"
}

// LiveWallpaperWeather: patch number = number of commits since the Breezy fork base, so the
// version bumps by one with every commit (1.0.0 -> 1.0.1 -> 1.0.2 ...).
fun Project.getLwwPatch(): Int {
    val base = "795e85b" // Breezy Weather base commit ("New cycle"), our fork point
    return try {
        runCommand("git rev-list --count $base..HEAD").toIntOrNull() ?: 0
    } catch (e: Exception) {
        0
    }
}

// LiveWallpaperWeather: build date for the versionName (yyyy.MM.dd.buildnr), so the visible
// version reads as a date instead of a semver-like number. Kept separate from versionCode,
// which must stay a monotonically increasing integer for the Play Store.
fun getBuildDate(): String {
    return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))
}

fun Project.runCommand(command: String): String {
    return providers.exec {
        commandLine = command.split(" ")
    }
        .standardOutput
        .asText
        .get()
        .trim()
}
