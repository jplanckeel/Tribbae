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

android {
    namespace = "com.linkkeeper.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.linkkeeper.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0" // x-release-please-version
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            // Backend visé par une build de dev : -Ptribbae.apiUrl=http://10.0.2.2:8080
            val apiUrl = (project.findProperty("tribbae.apiUrl") as String?) ?: ""
            buildConfigField("String", "API_URL", "\"$apiUrl\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "API_URL", "\"\"")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
