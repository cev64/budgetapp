# R8 rules for release builds.

# kotlinx.serialization: keep generated serializers for @Serializable classes (backup JSON,
# GoTrue responses, the add-sheet draft). The library ships most rules; these cover companions.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers @kotlinx.serialization.Serializable class com.personal.budget.** {
    *** Companion;
    static <fields>;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.personal.budget.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room / WorkManager / Glance receivers are referenced from generated code or the manifest and
# are kept by their own consumer rules. The worker is instantiated reflectively by WorkManager.
-keep class com.personal.budget.workers.SyncWorker { <init>(...); }

# OkHttp optional platform classes.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
