plugins { kotlin("jvm") }
kotlin { jvmToolchain(23) }

sourceSets.getByName("main") {
    kotlin.srcDirs("src/kotlin")
}

dependencies {
    implementation(libs.gson)
    //implementation(libs.commons.imaging)
}
