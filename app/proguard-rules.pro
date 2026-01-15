# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-dontobfuscate

# Keep source file and line numbers for better crash reports
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Keep all native methods and JNI classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI class
-keep class com.termux.terminal.JNI { *; }

# Keep LocalSocketManager native methods
-keep class com.termux.shared.net.socket.local.LocalSocketManager { *; }
-keep class com.termux.shared.net.socket.local.LocalSocketManagerClientBase { *; }
-keep class com.termux.shared.net.socket.local.LocalServerSocket { *; }
-keep class com.termux.shared.net.socket.local.LocalClientSocket { *; }

# Keep TermuxInstaller native methods
-keep class com.termux.app.TermuxInstaller { *; }

# Keep classes used via reflection
-keep class com.termux.shared.termux.TermuxBootstrap { *; }
-keep class com.termux.shared.termux.TermuxBootstrap$* { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Kotlin metadata for reflection
-keepattributes RuntimeVisibleAnnotations
