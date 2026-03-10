# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Apache POI
-dontwarn org.apache.poi.**
-keep class org.apache.poi.** { *; }

# Commons IO
-dontwarn commons.io.**
-keep class commons.io.** { *; }

# Commons Compress
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.compress.** { *; }

# XMLBeans (POI 依赖)
-dontwarn org.apache.xmlbeans.**
-keep class org.apache.xmlbeans.** { *; }
