import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "ir.mahdiparastesh"
version = "4.5.0"

sourceSets.getByName("main") { kotlin.srcDirs("main") }
sourceSets.getByName("test") { kotlin.srcDirs("test") }

dependencies {
    implementation(project(":core"))
}

tasks.named<KotlinJvmCompile>("compileKotlin") {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_24)
    }
}

tasks.jar {
    archiveBaseName = "InstaTools"
    manifest {
        attributes["Main-Class"] = "ir.mahdiparastesh.instatools.MainKt"
        attributes["Manifest-Version"] = version
    }

    from(configurations.runtimeClasspath.get().map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    file("exclusion.txt").readLines()
        .forEach { if (it.isNotBlank()) exclude(it) }
}

tasks.register("compileAot", Exec::class) {
    commandLine("${workingDir.absolutePath}/compile_aot.bat", version)
}
// Kotlin Native does not support Java utilities and requires the crowded multiplatform plugin.
