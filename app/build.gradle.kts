plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
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
        minSdk = 21
        targetSdk = 35
        versionCode = 77
        versionName = "29.4.2"
        signingConfig = signingConfigs.getByName("main")
    }
    sourceSets.getByName("main") {
        manifest.srcFile("src/AndroidManifest.xml")
        kotlin.setSrcDirs(listOf("src/kotlin"))
        res.setSrcDirs(listOf("src/res"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_22
        targetCompatibility = JavaVersion.VERSION_22
    }
    kotlinOptions { jvmTarget = "22" }

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
    implementation(libs.volley)
    implementation(libs.shimmer)
    implementation(libs.glide)
    implementation(libs.material)
    implementation(libs.gson)
    implementation(libs.dotsindicator)
    implementation(libs.commons.imaging)
    implementation(libs.commons.text) // StringEscapeUtils
}
