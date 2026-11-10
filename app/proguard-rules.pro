# PdfBox-Android loads resources and font classes reflectively.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**
# SQLCipher JNI entry points.
-keep class net.zetetic.database.** { *; }
# kotlinx.serialization generated serializers.
-keepclassmembers class com.easy2642.** { *** Companion; }
-keepclasseswithmembers class com.easy2642.** { kotlinx.serialization.KSerializer serializer(...); }
