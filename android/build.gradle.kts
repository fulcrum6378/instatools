plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ir.mahdiparastesh.instatools"
    compileSdk = 36
    buildToolsVersion = System.getenv("ANDROID_BUILD_TOOLS_VERSION")

    defaultConfig {
        applicationId = "ir.mahdiparastesh.instatools"
        minSdk = 26
        targetSdk = 36
        versionCode = 78
        versionName = "38.1.6"
    }

    sourceSets.getByName("main") {
        manifest.srcFile("AndroidManifest.xml")
        java.setSrcDirs(listOf("java"))
        kotlin.setSrcDirs(listOf("kotlin"))
        res.setSrcDirs(listOf("res"))
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_23
        targetCompatibility = JavaVersion.VERSION_23
    }
    kotlinOptions { jvmTarget = "23" }

    signingConfigs {
        create("main") {
            storeFile = file(System.getenv("JKS_PATH"))
            storePassword = System.getenv("JKS_PASS")
            keyAlias = "instatools"
            keyPassword = System.getenv("JKS_PASS")
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("main")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("main")
        }
    }
    lint { checkReleaseBuilds = false }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.ktx.activity)
    implementation(libs.constraintlayout)
    implementation(libs.ktx.core)
    implementation(libs.drawerlayout)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.recyclerview)
    implementation(libs.recyclerview.selection)
    implementation(libs.swiperefreshlayout)
    implementation(libs.viewpager2)
    implementation(libs.webkit)
    implementation(libs.lottie)
    implementation(libs.shimmer)
    implementation(libs.glide)
    implementation(libs.material)
    implementation(libs.dotsindicator)
    implementation(libs.commons.text) // StringEscapeUtils
    implementation(libs.coroutines)
    implementation(libs.serialization.json)
}
