import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.core.splashscreen)
            // Analyse HTML des pages liées (images, titre) quand le backend n'est pas joignable
            implementation(libs.jsoup)
        }
        commonMain.dependencies {
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.compose.material.icons)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

// Version de l'app, montée par release-please. Le Play Store exige un
// versionCode qui augmente à chaque envoi : il en est calculé
// (major * 10000 + minor * 100 + patch, ex. 1.4.12 -> 10412).
val appVersion = "0.2.0" // x-release-please-version
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "versionCode: minor et patch doivent rester sous 100 ($appVersion)" }
    major * 10000 + minor * 100 + patch
}

android {
    // Paquet du code Kotlin (R, BuildConfig) ; l'identifiant sur le Play Store est applicationId
    namespace = "com.linkkeeper.app"
    compileSdk = 36

    defaultConfig {
        // Identifiant définitif sur le Play Store (ne peut plus changer après le premier envoi)
        applicationId = "fr.tribbae.app"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    buildFeatures {
        buildConfig = true
    }
    // Signature des APK publiés : release.yml fournit le keystore (secret
    // ANDROID_KEYSTORE_BASE64) et ses mots de passe. Sans ces variables (build
    // locale), l'APK release sort non signé, et Android refuse de l'installer.
    val releaseKeystore = System.getenv("ANDROID_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                // v2/v3, exigées par les Android récents (v1 inutile : minSdk 24)
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // Backend visé par une build de dev : -Ptribbae.apiUrl=http://10.0.2.2:8080
            val apiUrl = (project.findProperty("tribbae.apiUrl") as String?) ?: ""
            buildConfigField("String", "API_URL", "\"$apiUrl\"")
        }
        getByName("release") {
            // R8 : réduit, optimise et renomme le code, retire les ressources inutilisées
            // (surtout material-icons-extended, dont seules quelques icônes servent).
            // Règles propres à l'app dans proguard-rules.pro ; kotlinx.serialization,
            // Coil, Compose et jsoup apportent les leurs.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_URL", "\"\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
