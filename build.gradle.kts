// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
}

fun List<String>.execute(workingDir: File): String {
    return try {
        ProcessBuilder(this)
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
            .inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        logger.warn("Failed to execute git command: ${e.message}")
        "unknown" // fallback value
    }
}

val gitCommitCountString = listOf("git", "rev-list", "--count", "HEAD").execute(project.rootDir).trim()
val gitCommitCountInt = gitCommitCountString.toInt()

extra.apply {
    set("gitCommitCountString", gitCommitCountString)
    set("gitCommitCountInt", gitCommitCountInt)
}