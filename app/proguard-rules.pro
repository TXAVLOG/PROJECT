# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Room entities
-keep class ms.txams.vv.data.database.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keepclassmembers class * {
    @dagger.hilt.* <fields>;
}
