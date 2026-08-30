# JNI bridge must never be stripped/renamed (spec 0010).
-keep class com.agentai.app.whisper.WhisperLib { *; }
-keepclassmembers class com.agentai.app.whisper.WhisperLib { native <methods>; }