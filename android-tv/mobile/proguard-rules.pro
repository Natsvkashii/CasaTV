# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlinx.serialization.**
-dontwarn okhttp3.**
-dontwarn okio.**

-keep class it.casatv.mobile.cast.** { *; }
-keep class it.casatv.mobile.data.** { *; }
-keep class com.google.android.gms.cast.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class androidx.media3.** { *; }
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.datasource.rtmp.** { *; }
