plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Automatically heal nested app/app/ upload and remove legacy Nov 4 package/drawable files
val nestedAppSrc = file("app/src")
if (nestedAppSrc.exists()) {
    nestedAppSrc.copyRecursively(file("src"), overwrite = true)
    file("app").deleteRecursively()
}
file("src/main/java/ba/vaktija/premium").let { if (it.exists()) it.deleteRecursively() }
file("src/main/java/ba/vaktija/nova/ui").let { if (it.exists()) it.deleteRecursively() }
listOf(
    "src/main/res/drawable/ic_launcher_foreground.xml",
    "src/main/res/drawable/ic_vaktija_launcher.xml",
    "src/main/res/drawable/ic_stat_vaktija_default.xml"
).forEach { rel ->
    val f = file(rel)
    if (f.exists()) f.delete()
}

android {
    namespace = "ba.vaktija.nova"
    compileSdk = 34

    defaultConfig {
        applicationId = "ba.vaktija.nova"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
