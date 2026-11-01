import java.text.SimpleDateFormat
import java.util.Date
import java.util.regex.Pattern

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.doswriter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.doswriter"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Custom task to copy and label APKs in standard output locations
tasks.register("renameAllApks") {
    doLast {
        val date = SimpleDateFormat("MMddyyyy").format(Date())
        val searchDirs = listOf(
            file("${layout.buildDirectory.get().asFile}/outputs/apk/release"),
            file("${layout.buildDirectory.get().asFile}/outputs/apk/debug"),
            file("${projectDir}/release"),
            file("${projectDir}/debug")
        )
        
        searchDirs.forEach { dir ->
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.extension == "apk" && !file.name.startsWith("DWTEv1.0")) {
                        val variant = if (file.name.contains("debug")) "debug" else "release"
                        val newName = "DWTEv1.0-$date-app-$variant.apk"
                        val destFile = File(dir, newName)
                        file.copyTo(destFile, overwrite = true)
                        println("Copied ${file.name} to $newName")
                    }
                }
            }
        }
    }
}

tasks.whenTaskAdded {
    if (name.startsWith("assemble") || name.startsWith("bundle")) {
        finalizedBy("renameAllApks")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
