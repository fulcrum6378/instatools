plugins { kotlin("jvm") }
kotlin { jvmToolchain(23) }

group = "ir.mahdiparastesh"
version = "3.1.5"

dependencies {
    implementation(project(":core"))

    implementation(libs.gson)
    implementation(libs.commons.imaging)
}

tasks.jar {
    archiveBaseName = "InstaTools"
    manifest {
        attributes["Main-Class"] = "ir.mahdiparastesh.instatools.MainKt"
        attributes["Manifest-Version"] = version
    }
    from(configurations.runtimeClasspath.get().map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
