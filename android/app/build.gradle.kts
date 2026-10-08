plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.derricknoutais.tikeo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.derricknoutais.tikeo"
        // Le Sunmi V2 Pro tourne sous Android 7.1 (API 25) ; le ZCS Z92S sous Android 16.
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Sert la page de test embarquée sous une vraie origine https.
    implementation("androidx.webkit:webkit:1.12.1")
    // Un pilote par marque, chacun dans son dépôt (voir settings.gradle.kts).
    implementation(project(":pilote-sunmi"))
    implementation(project(":pilote-zcs"))
}
