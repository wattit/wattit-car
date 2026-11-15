# Release builds shrink unused code and rename what is not kept, so library internals do not carry
# readable names into the APK. Nothing is rewritten (no optimization). The Pleos SDK, JNA and Gson keep
# their names through the rules below and their own consumer rules, so reflection and IPC still see the
# same classes. Keep build/outputs/mapping/release/mapping.txt outside the repository to read crash traces.
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
