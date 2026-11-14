# Offline voice (Vosk) talks to native code through JNA, by name.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# Map
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.**
-dontwarn com.google.auto.value.**
