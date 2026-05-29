import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localOrEnv(vararg names: String, default: String = ""): String {
    names.forEach { name ->
        localProperties.getProperty(name)?.let { return it }
        System.getenv(name)?.let { return it }
    }
    return default
}

fun quote(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.androidengineers.agent_quickstart_android"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.androidengineers.agent_quickstart_android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "AGORA_APP_ID",
            quote(localOrEnv("AGORA_APP_ID", "agora.app.id"))
        )
        buildConfigField(
            "String",
            "AGORA_BACKEND_BASE_URL",
            quote(localOrEnv("AGORA_BACKEND_BASE_URL", "http://10.0.2.2:3000"))
        )
        buildConfigField("int", "AGENT_UID", localOrEnv("AGORA_AGENT_UID", default = "123456"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            pickFirsts += listOf(
                "**/libaosl.so",
                "**/libagora_rtc_sdk.so",
                "**/libagora_core.so",
                "**/libagora_fdkaac.so",
                "**/libagora_soundtouch.so"
            )
        }
    }
}

dependencies {
    implementation(libs.agora.rtc)
    implementation(libs.agora.rtm)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
