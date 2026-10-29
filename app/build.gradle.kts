import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.barshan.aiphotoeditor"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.barshan.aiphotoeditor.beta"
        minSdk = 26
        targetSdk = 35
        // The shipped APK targets modern 64-bit Android phones and avoids bundling unused x86/32-bit native runtimes.
        ndk { abiFilters += listOf("arm64-v8a") }
        versionCode = 23
        versionName = "2.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            // CI uses the stable debug keystore for a signed, installable test release.
            // Replace this with a private production signing key before public distribution.
            signingConfig = signingConfigs.getByName("debug")
            // Shrink unused bytecode and resources in distributable builds.
            // AI models in app/src/main/assets are retained and their behavior is unchanged.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
// Large AI models are downloaded on first use into private app storage.
// Keeping these two optional engines out of the APK reduces install size without removing their features.
// ESRGAN remains bundled because it is relatively small and is used directly by the upscaler.
val esrganModelFile = layout.projectDirectory.file("src/main/assets/ESRGAN.tflite")
tasks.register("downloadEsrganSuperResolutionModel") {
    outputs.file(esrganModelFile)
    doLast {
        val model = esrganModelFile.asFile
        if (!model.exists() || model.length() < 1_000_000L) {
            model.parentFile.mkdirs()
            val temp = File(model.parentFile, model.name + ".download")
            URL("https://storage.googleapis.com/download.tensorflow.org/models/tflite/esrgan/ESRGAN.tflite").openStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            check(temp.length() > 1_000_000L) { "ESRGAN super-resolution model download was incomplete." }
            check(temp.renameTo(model)) { "Could not place the ESRGAN model in app/src/main/assets." }
        }
    }
}
tasks.named("preBuild").configure {
    dependsOn("downloadEsrganSuperResolutionModel")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
