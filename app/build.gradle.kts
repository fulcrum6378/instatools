plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
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
        versionName = "29.0.1"
        signingConfig = signingConfigs.getByName("main")
    }
    sourceSets.getByName("main") {
        manifest.srcFile("src/AndroidManifest.xml")
        kotlin.setSrcDirs(listOf("src/kotlin"))
        res.setSrcDirs(listOf("src/res"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_22; targetCompatibility = JavaVersion.VERSION_22
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
            matchingFallbacks.add("debug") // temporarily for DotsIndicator
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
    val roomVersion = "2.6.1"
    val media3Version = "1.4.1"

    implementation("androidx.activity:activity-ktx:1.9.2") // only for ActivityResultLauncher
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.recyclerview:recyclerview-selection:1.1.0")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.12.0")
    implementation("com.airbnb.android:lottie:6.2.0")
    implementation("com.android.volley:volley:1.2.1")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.tbuonomo:dotsindicator:5.0")
    implementation("ir.mahdiparastesh:chipslayoutmanager:0.5.0")
    implementation("org.apache.commons:commons-imaging:1.0-alpha3")
    implementation("org.apache.commons:commons-text:1.11.0") // StringEscapeUtils
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
