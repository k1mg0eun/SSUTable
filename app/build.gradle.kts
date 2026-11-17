plugins {
    id("com.android.application")
}

android {
    namespace = "com.g1j2.ssutable"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.g1j2.ssutable"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-demo"
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}
