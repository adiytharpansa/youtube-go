# YouTube GO - keep serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.app.youtubego.**$$serializer { *; }
-keep class com.app.youtubego.data.remote.dto.** { *; }
