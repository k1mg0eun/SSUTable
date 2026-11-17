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

    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-auth")

    testImplementation("junit:junit:4.13.2")
}

// google-services.json이 있을 때만 Firebase 설정 플러그인 적용
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
