plugins {
    id(libs.plugins.android.application.get().pluginId)
    id(libs.plugins.kotlin.compose.get().pluginId)
}

android {
    namespace = "com.machine_check.inspection"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.machine_check.inspection"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // ========== Android 核心 ==========
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ========== Jetpack Compose ==========
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // ========== Navigation ==========
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // ========== ViewModel + Compose 集成 ==========
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    // ========== DataStore ==========
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ========== CameraX 扫码 ==========
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-mlkit-vision:1.4.2")

    // ========== ML Kit 条码识别 ==========
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // ========== ZXing 条码解码 ==========
    implementation("com.google.zxing:core:3.5.3")

    // ========== 网络请求 ==========
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ========== 测试 ==========
    testImplementation(libs.junit)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // ========== Debug 工具 ==========
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

