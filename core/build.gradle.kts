plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(23) }

sourceSets.getByName("main") {
    kotlin.srcDirs("kotlin")
}

dependencies {
    implementation(libs.commons.imaging)
    implementation(libs.serialization.json)
}
