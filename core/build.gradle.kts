import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

tasks.named<KotlinJvmCompile>("compileKotlin") {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_24)
    }
}

sourceSets.getByName("main") {
    kotlin.srcDir("main")
}
sourceSets.getByName("test") {
    kotlin.srcDir("test/kotlin")
    resources.srcDir("test/resources")
}

dependencies {
    implementation(libs.serialization.json)
    testImplementation(libs.junit.jupiter)
}
