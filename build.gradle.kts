plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.register("clean", Delete::class) {
    delete(
        "$rootDir/build",
        "$rootDir/core/build",
        "$rootDir/clie/build",
        "$rootDir/android/build",
        "$rootDir/.kotlin",
    )
}
