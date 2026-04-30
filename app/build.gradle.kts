plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // NUEVO: Aplicar el plugin de KSP
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.nfc_reader_01"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.nfc_reader_01"
        minSdk = 26
        targetSdk = 35

        versionCode = 2
        versionName = "Rev 2.12"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AGREGA ESTO COMO SALVAVIDAS PARA EL COMPILADOR:
        buildConfigField("String", "UI_MODE", "\"DEFAULT\"")
    }

    // CORRECCIÓN 1: Habilitar explícitamente BuildConfig para que funcionen los campos personalizados
    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

    // CORRECCIÓN 2: Sintaxis correcta para definir dimensiones en Kotlin DSL
    flavorDimensions += "version"

    productFlavors {
        // APP 1: Solo Apagado
        create("shutdown") {
            dimension = "version"
            applicationIdSuffix = ".shutdown"
            versionNameSuffix = "-SHUT"
            // Definimos el modo como SHUTDOWN
            buildConfigField("String", "UI_MODE", "\"SHUTDOWN\"")
        }

        // APP 2: Solo Calibración
        create("calibration") {
            dimension = "version"
            applicationIdSuffix = ".calibration"
            versionNameSuffix = "-CAL"
            // Definimos el modo como CALIBRATION
            buildConfigField("String", "UI_MODE", "\"CALIBRATION\"")
        }

        // APP 3: Full (Todo visible)
        create("full") {
            dimension = "version"
            applicationIdSuffix = ".full"
            versionNameSuffix = "-FULL"
            // Definimos el modo como FULL
            buildConfigField("String", "UI_MODE", "\"FULL\"")
        }
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
        // Agrega esta línea con la sintaxis correcta para Kotlin DSL
        freeCompilerArgs = freeCompilerArgs + "-Xskip-metadata-version-check"
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

    // Versiones directas estables de Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    // ¡ESTA ES LA LÍNEA CRÍTICA QUE FALTA!:
    ksp("androidx.room:room-compiler:$room_version")
}