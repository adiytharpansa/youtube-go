# Playtube LITE - keep serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.app.playtubelite.**$$serializer { *; }
-keep class com.app.playtubelite.data.remote.dto.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.app.playtubelite.** { *** Companion; }
-keepclasseswithmembers class com.app.playtubelite.** { kotlinx.serialization.KSerializer serializer(...); }
# Dipanggil lewat refleksi (ClientHeaders.extractorUserAgent): jangan di-rename/dibuang oleh R8.
-keepclassmembers class org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper { public static java.lang.String get*UserAgent(...); }

# NewPipeExtractor di build release (R8): tanpa aturan ini ekstraksi gagal atau build berhenti karena kelas Rhino/JSR-223 yang tak ada.
# Pola "x waktu lalu" dimuat lewat refleksi berdasarkan nama kelas.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn com.google.protobuf.**
