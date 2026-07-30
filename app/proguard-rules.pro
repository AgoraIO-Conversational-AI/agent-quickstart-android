# Keep stack traces readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Agora RTC / RTM ──────────────────────────────────────────────────────────
# Agora uses JNI callbacks and reflection; its classes must not be renamed.
-keep class io.agora.** { *; }
-keep interface io.agora.** { *; }
-dontwarn io.agora.**

# ── Retrofit + OkHttp ────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Gson (used by Retrofit converter) ────────────────────────────────────────
# Keep data classes that Gson serialises/deserialises via reflection.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Keep our own API model classes that Gson touches.
-keep class com.androidengineers.agent_quickstart_android.data.** { *; }

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── Jetpack Compose ───────────────────────────────────────────────────────────
# R8 ships Compose-aware rules; these cover the edge cases it misses.
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── BuildConfig ───────────────────────────────────────────────────────────────
-keep class com.androidengineers.agent_quickstart_android.BuildConfig { *; }
