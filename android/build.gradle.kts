plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ir.mahdiparastesh.instatools"
    compileSdk = 35
    buildToolsVersion = System.getenv("ANDROID_BUILD_TOOLS_VERSION")

    signingConfigs {
        create("main") {
            storeFile = file(System.getenv("JKS_PATH"))
            storePassword = System.getenv("JKS_PASS")
            keyAlias = "instatools"
            keyPassword = System.getenv("JKS_PASS")
        }
    }

    defaultConfig {
        applicationId = "ir.mahdiparastesh.instatools"
        minSdk = 21 // TODO make it 26
        targetSdk = 35
        versionCode = 77
        versionName = "33.5.0"
        signingConfig = signingConfigs.getByName("main")
    }
    sourceSets.getByName("main") {
        manifest.srcFile("AndroidManifest.xml")
        kotlin.setSrcDirs(listOf("kotlin"))
        res.setSrcDirs(listOf("res"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_23
        targetCompatibility = JavaVersion.VERSION_23
    }
    kotlinOptions { jvmTarget = "23" }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    buildTypes {
        create("debuggee") {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
}
androidComponents.beforeVariants { variantBuilder ->
    if (variantBuilder.buildType in listOf("debug", "androidTest"))
        variantBuilder.enable = false
}

dependencies {
    implementation(project(":core"))

    implementation(libs.ktx.activity) // only for ActivityResultLauncher
    implementation(libs.ktx.core)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.recyclerview.selection)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)
    implementation(libs.swiperefreshlayout)
    implementation(libs.webkit)
    implementation(libs.lottie)
    implementation(libs.shimmer)
    implementation(libs.glide)
    implementation(libs.material)
    implementation(libs.gson)
    implementation(libs.dotsindicator)
    implementation(libs.commons.imaging)
    implementation(libs.commons.text) // StringEscapeUtils
    implementation(libs.serialization.json)
}
