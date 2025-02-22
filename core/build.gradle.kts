plugins { kotlin("jvm") }
kotlin { jvmToolchain(23) }

sourceSets.getByName("main") {
    kotlin.srcDirs("src/kotlin")
}
