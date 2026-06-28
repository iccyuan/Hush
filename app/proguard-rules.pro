# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.iccyuan.hush.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.iccyuan.hush.data.model.**$$serializer { *; }

# Keep the (de)serialized data model intact — these are persisted as JSON columns and
# round-tripped via polymorphic serializers, so member/name stripping must not touch them.
-keep class com.iccyuan.hush.data.model.** { *; }

# Room: keep entities and generated DAO/database implementations.
-keep class com.iccyuan.hush.data.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# TTS / notification reflection-free, but keep enum values used via valueOf in converters.
-keepclassmembers enum com.iccyuan.hush.data.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
