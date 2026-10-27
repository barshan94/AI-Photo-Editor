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
        versionCode = 23
        versionName = "2.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
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
// The official Apache-2.0 MediaPipe multiclass segmentation model is fetched during builds.
// It is not committed as a binary asset; the same pinned model is used by CI and local builds.
val segmentationModelFile = layout.projectDirectory.file("src/main/assets/selfie_multiclass_256x256.tflite")
val esrganModelFile = layout.projectDirectory.file("src/main/assets/ESRGAN.tflite")
val lamaModelFile = layout.projectDirectory.file("src/main/assets/lama_512_int8.onnx")
tasks.register("downloadMulticlassSegmentationModel") {
    outputs.file(segmentationModelFile)
    doLast {
        val model = segmentationModelFile.asFile
        if (!model.exists() || model.length() < 100_000L) {
            model.parentFile.mkdirs()
            val temp = File(model.parentFile, model.name + ".download")
            URL("https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/1/selfie_multiclass_256x256.tflite").openStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            check(temp.length() > 100_000L) { "MediaPipe segmentation model download was incomplete." }
            check(temp.renameTo(model)) { "Could not place the MediaPipe segmentation model in app/src/main/assets." }
        }
    }
}
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
tasks.register("downloadLamaInpaintingModel") {
    outputs.file(lamaModelFile)
    doLast {
        val model = lamaModelFile.asFile
        if (!model.exists() || model.length() < 20_000_000L) {
            model.parentFile.mkdirs()
            val temp = File(model.parentFile, model.name + ".download")
            URL("https://huggingface.co/g-ronimo/lama/resolve/main/lama_512_int8.onnx").openStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            check(temp.length() > 20_000_000L) { "LaMa inpainting model download was incomplete." }
            check(temp.renameTo(model)) { "Could not place the LaMa model in app/src/main/assets." }
        }
    }
}
tasks.named("preBuild").configure {
    dependsOn("downloadMulticlassSegmentationModel")
    dependsOn("downloadEsrganSuperResolutionModel")
    dependsOn("downloadLamaInpaintingModel")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
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
