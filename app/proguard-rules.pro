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


# Preserve runtime metadata required by Room and Moshi adapters.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-keep class **_Impl { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * { @androidx.room.* <fields>; @androidx.room.* <methods>; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * { @com.squareup.moshi.Json <fields>; }

# Keep Android framework entry points and transport serialization contracts stable.
-keep class com.veil.privacy.notification.VeilNotificationListener { *; }
-keep class com.veil.privacy.notification.VeilNotificationReplyReceiver { *; }
-keep class com.veil.privacy.service.VeilProtectionService { *; }
-keep class com.veil.privacy.licensing.** { *; }
-keep class com.veil.privacy.communication.VeilTransportPacket { *; }
