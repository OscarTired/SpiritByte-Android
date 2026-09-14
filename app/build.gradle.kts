plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.spiritbyte.android"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.spiritbyte.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0-dev"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
}

tasks.register("verifyNative") {
    doLast {
        listOf("arm64-v8a", "x86_64").forEach {
            check(file("src/main/jniLibs/$it/libspiritbyte_mobile.so").isFile) {
                "Missing Rust library for $it. Run scripts/build-native.ps1 first."
            }
        }
        check(file("src/main/java/com/spiritbyte/core/spiritbyte_mobile.kt").isFile) {
            "Missing UniFFI bindings. Run scripts/build-native.ps1 first."
        }
    }
}
tasks.named("preBuild") { dependsOn("verifyNative") }
