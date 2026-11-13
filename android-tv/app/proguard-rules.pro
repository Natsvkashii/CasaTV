# CasaTV currently ships unminified. Keep these rules for a future release build.
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
