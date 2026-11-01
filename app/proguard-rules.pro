# Standard workstation rules for signed APKs
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep DOSwriter model and logic classes for Gson and Reflection
-keep class com.example.doswriter.model.** { *; }
-keep class com.example.doswriter.logic.** { *; }
-keep class com.example.doswriter.editor.** { *; }

# Keep Onyx SDK classes for HAL reflection
-keep class com.onyx.** { *; }

# Gson specific rules to preserve TypeTokens
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements com.google.gson.TypeAdapterFactory
-keep public class * implements com.google.gson.JsonSerializer
-keep public class * implements com.google.gson.JsonDeserializer