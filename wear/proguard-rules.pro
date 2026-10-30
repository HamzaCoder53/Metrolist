# kotlinx.serialization models used by :innertube and innertubex
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers @kotlinx.serialization.Serializable class ** { *; }
-keep,includedescriptorclasses class com.metrolist.**$$serializer { *; }
-keepclassmembers class com.metrolist.** { *** Companion; }
-keep class com.metrolist.innertube.models.** { *; }
-keep class com.metrolist.innertubex.** { *; }

# PoToken WebView JavaScript bridge
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
