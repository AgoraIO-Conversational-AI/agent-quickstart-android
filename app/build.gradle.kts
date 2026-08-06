import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
            "AGORA_APP_CERTIFICATE",
            quote(localOrEnv("AGORA_APP_CERTIFICATE"))
        )
        buildConfigField(
            "String",
            "AGORA_CONVOAI_BASE_URL",
            quote(
                localOrEnv(
                    "AGORA_CONVOAI_BASE_URL",
                    default = "https://api.agora.io/api/conversational-ai-agent/v2/projects"
                )
            )
        )
        buildConfigField(
            "String",
            "AGORA_AREA",
            quote(localOrEnv("AGORA_AREA", default = "US"))
        )
        buildConfigField("int", "AGENT_UID", localOrEnv("AGORA_AGENT_UID", default = "123456"))
        buildConfigField(
            "String",
            "MURF_API_KEY",
            quote(localOrEnv("MURF_API_KEY"))
        )
        buildConfigField(
            "String",
            "MURF_BASE_URL",
            quote(
                localOrEnv(
                    "MURF_BASE_URL",
                    default = "wss://in.api.murf.ai/v1/speech/stream-input"
                )
            )
        )
        buildConfigField(
            "String",
            "MURF_VOICE_ID",
            quote(localOrEnv("MURF_VOICE_ID", default = "Anisha"))
        )
        buildConfigField(
            "String",
            "MURF_LOCALE",
            quote(localOrEnv("MURF_LOCALE", default = "en-IN"))
        )
        buildConfigField(
            "String",
            "MURF_MODEL",
            quote(localOrEnv("MURF_MODEL", default = "FALCON"))
        )
        buildConfigField(
            "String",
            "AGORA_ASR_VENDOR",
            quote(localOrEnv("AGORA_ASR_VENDOR", default = "deepgram"))
        )
        buildConfigField(
            "String",
            "AGORA_ASR_LANGUAGE",
            quote(localOrEnv("AGORA_ASR_LANGUAGE", default = "en"))
        )
        buildConfigField(
            "String",
            "SARVAM_API_KEY",
            quote(localOrEnv("SARVAM_API_KEY"))
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Keep debug fast — R8 only runs for release.
            isMinifyEnabled = false
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room3.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary)
}
