# Keep NanoHTTPD internals used through reflection-free entry points.
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class it.casatv.server.** { *; }
