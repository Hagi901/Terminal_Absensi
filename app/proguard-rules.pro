# ===================================================================
# ProGuard / R8 Rules untuk Terminal Absensi
# ===================================================================

# 1. OpenCV JNI (Wajib agar C++ native binding tidak terpotong R8)
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# 2. Room Database & Entities
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-keep class com.example.terminalabsensi.data.local.** { *; }

# 3. Koin Dependency Injection
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# 4. CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# 5. Model Classes & UseCases
-keep class com.example.terminalabsensi.domain.** { *; }
-keep class com.example.terminalabsensi.facerecognition.** { *; }

# 6. Preserve Annotations & Signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod