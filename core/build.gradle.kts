plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(23) }

sourceSets.getByName("main") {
    kotlin.srcDirs("kotlin")
}

dependencies {
    implementation(libs.serialization.json)
}
