# osmdroid keeps a reference to a few classes it instantiates reflectively.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.example.indriveclone.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.indriveclone.** {
    kotlinx.serialization.KSerializer serializer(...);
}
