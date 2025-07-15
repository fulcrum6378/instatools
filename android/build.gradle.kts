import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        minSdk = 29
        targetSdk = 36
        versionCode = 79
        versionName = "42.6.3"
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
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
    }
    kotlin {
        target {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_24)
                freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
            }
        }
    }

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
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("main")
        }
    }
    lint { checkReleaseBuilds = false }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.ktx.core)  // used by many UI libraries
    implementation(libs.documentfile)
    implementation(libs.drawerlayout)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.recyclerview)
    implementation(libs.recyclerview.selection)
    implementation(libs.swiperefreshlayout)
    implementation(libs.viewpager2)
    implementation(libs.lottie)
    implementation(libs.shimmer)
    implementation(libs.glide)
    implementation(libs.dotsindicator) {
        exclude(group = "androidx.activity", module = "activity-compose")
        exclude(group = "androidx.cardview")
        exclude(group = "androidx.compose")
        exclude(group = "androidx.compose.material3")
        exclude(group = "androidx.compose.ui")
        exclude(group = "androidx.dynamicanimation")
    }
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
}

configurations.all {
    exclude(group = "androidx.appcompat")
}
