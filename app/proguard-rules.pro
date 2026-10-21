# YouTube GO : Next - keep serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.app.youtubego.**$$serializer { *; }
-keep class com.app.youtubego.data.remote.dto.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.app.youtubego.** { *** Companion; }
-keepclasseswithmembers class com.app.youtubego.** { kotlinx.serialization.KSerializer serializer(...); }
# Dipanggil lewat refleksi (PlayerHolder.ytUserAgent): jangan di-rename/dibuang oleh R8.
-keepclassmembers class org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper { public static java.lang.String get*UserAgent(...); }
