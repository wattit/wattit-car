# Release builds only shrink unused code. Names stay as they are and nothing is rewritten, so reflection
# and IPC inside the Pleos SDK see the same classes as in a debug build.
-dontobfuscate
-dontoptimize

# Pleos Connect SDK (Vehicle, TextToSpeech, their AIDL, data and security modules). The SDK ships its own
# consumer rules; keeping every ai.pleos class is a wider net for its service binding, Gson data classes
# and permission lookups, which no device test has covered yet.
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-keepclassmembers enum ai.pleos.** { *; }

# JNA (the SDK's security module loads libuacore through it).
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# Gson is used by the SDK on its own data classes (kept above); keep its type adapters and generic signatures.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.google.gson.** { *; }

# Android Automotive Car API is provided by the system image.
-dontwarn android.car.**
