import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp) // Runs Room's code generator.
}

// Release signing: the key and its passwords live in keystore.properties at the project root, which git
// ignores (see keystore.properties.example). Without that file the release build still works, just unsigned.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "one.mindly.pip"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "one.mindly.pip"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The sherpa-onnx AAR ships native code for 4 CPU types (~120 MB unpacked).
        // The Galaxy A32 and Apple-silicon emulators are both arm64, so we only package that one.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            // abiFilters drops these from the APK, but the native-library merge runs first and would
            // trip over sherpa's and onnxruntime-android's x86 copies of libonnxruntime.so.
            excludes += setOf("lib/x86/**", "lib/x86_64/**", "lib/armeabi-v7a/**")
        }
    }
}

ksp {
    // Room writes a JSON snapshot of each database version here, which we need for future migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // sherpa-onnx prebuilt Android library (voice activity detection + offline speech-to-text), from
    // https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1. We use the build with ONNX Runtime
    // linked INSIDE it, so it doesn't clash with onnxruntime-android's libonnxruntime.so below
    // (their exported symbols carry different version tags: VERS_1.28.2 vs VERS_1.28.0).
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-2.0.1.aar"))
    // Runs our own "Hey Pip" model (training/) from Kotlin.
    implementation(libs.onnxruntime.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx) // Background transcription.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
