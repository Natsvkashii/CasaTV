# CasaTV currently ships unminified. Keep these rules for a future release build.
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }

-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.datasource.rtmp.** { *; }
