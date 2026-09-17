plugins {
    id("com.android.application")
}

android {
    namespace = "vn.thanhtruong.maplinksender"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.thanhtruong.maplinksender"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        setProperty("archivesBaseName", "MapLink-S24-Sender_${versionName}")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
