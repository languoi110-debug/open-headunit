plugins {
    id("com.android.application")
}

android {
    namespace = "vn.thanhtruong.maplinkreceiver"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.thanhtruong.maplinkreceiver"
        minSdk = 21
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"
        setProperty("archivesBaseName", "MapLink-J2-Standalone_${versionName}")
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
