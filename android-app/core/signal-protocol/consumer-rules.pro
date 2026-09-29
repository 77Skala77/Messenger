# libsignal's native library (libsignal_jni.so) looks up Java classes, methods and fields by name
# through JNI and calls back into Java store/callback interfaces. R8 must not rename, remove or
# inline them. libsignal-android ships no consumer rules of its own.
# Status: UNVERIFIED in a minified release build (step 1c). Verify with a release build that
# actually contains libsignal before relying on it, and narrow only with evidence.
-keep class org.signal.libsignal.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
