plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bichoelproxd.chromaticscalegenerator"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.bichoelproxd.chromaticscalegenerator"
        minSdk = 24 // Praat usa fseeko/ftello con _FILE_OFFSET_BITS=64: en 32 bits requiere API 24+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
                // ccache acelera mucho las compilaciones repetidas de Praat (lo activa el workflow con -PuseCcache)
                if (project.hasProperty("useCcache")) {
                    arguments += listOf(
                        "-DCMAKE_C_COMPILER_LAUNCHER=ccache",
                        "-DCMAKE_CXX_COMPILER_LAUNCHER=ccache"
                    )
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Un APK por arquitectura (armeabi-v7a y arm64-v8a) y ademas un APK "universal"
    // que contiene las dos, para instalarlo en cualquier telefono.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            // Se firma con la clave de depuracion para poder instalar el APK directamente.
            // (Para publicar en Google Play hay que usar una clave propia.)
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        jniLibs {
            // Mantener las librerias sin comprimir y alineadas (lo recomendado para Android 6+)
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
