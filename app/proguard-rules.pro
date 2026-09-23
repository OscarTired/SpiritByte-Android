# UniFFI/JNA use reflected native methods, structures and callbacks.
-keep class com.spiritbyte.core.** { *; }
-keep class com.sun.jna.** { *; }
# JNA also includes desktop AWT helpers, which are never called on Android.
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
