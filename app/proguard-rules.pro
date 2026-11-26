# Preserve line numbers and file names for Play Console / R8 retrace stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Room database & entities used via generated code
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# Keep BuildConfig fields
-keepclassmembers class com.example.BuildConfig {
    public static final <fields>;
}
