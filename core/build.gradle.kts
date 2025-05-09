plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(23) }

sourceSets.getByName("main") {
    kotlin.srcDirs("kotlin")
}
sourceSets.getByName("test") {
    kotlin.srcDirs("test")
}

dependencies {
    implementation(libs.serialization.json)
    testImplementation(libs.junit.jupiter)
}
