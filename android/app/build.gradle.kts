plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.codro.listenstudy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codro.listenstudy"
        minSdk = 26
        targetSdk = 35
        versionCode = 39
        versionName = "0.14.0-supporter-billing"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    // Plain billing artifact on purpose: the adapter uses only the callback API, and billing-ktx
    // would force a Kotlin/KSP toolchain upgrade for its Kotlin 2.3 metadata.
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    // billing:9.1.0 -> play-services-basement:18.9.0 drags in androidx.fragment:fragment:1.1.0
    // transitively, but MainActivity uses registerForActivityResult, which lint
    // (InvalidFragmentVersionForActivityResult) requires Fragment >= 1.3.0 for. Pin a direct,
    // stable Fragment: 1.8.5 matches the 2025 AndroidX stack (activity 1.10.0, lifecycle 2.8.7,
    // compileSdk 35) and its Kotlin 1.8-era metadata needs no Kotlin/KSP/Room baseline change.
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
