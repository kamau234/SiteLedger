# Keep rules for release builds.

# Room / Kotlin metadata
-keep class * extends androidx.room.RoomDatabase { *; }

# kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.siteledger.app.**$$serializer { *; }
-keepclassmembers class com.siteledger.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.siteledger.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Supabase kt
-dontwarn io.github.jan.supabase.**
