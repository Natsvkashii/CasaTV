# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-dontwarn kotlinx.serialization.**

-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.datasource.rtmp.** { *; }
