plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    // Se mantiene el namespace
    namespace = "com.example.nfc_reader_01"

    // FIX FINAL: Actualizado a API 35 (o superior) para satisfacer a las dependencias.
    compileSdk = 35


    defaultConfig {
        applicationId = "com.example.nfc_reader_01.only_shutdown"
        // CLAVE: minSdk se queda en 26 para mantener la compatibilidad con Android 8.0.
        minSdk = 26
        // ACTUALIZADO: targetSdk debe coincidir con compileSdk.
        targetSdk = 35
        versionCode = 1
        versionName = "Rev.8.01.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Dependencias Lifecycle
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Versión estable para runtime-ktx:
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Versiones directas estables de Navigation (2.7.7)
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
