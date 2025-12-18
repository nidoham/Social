# Firestore Model Classes - CRITICAL for deserialization
-keepclassmembers class com.nidoham.social.model.** {
    <init>();
    <fields>;
}

-keep class com.nidoham.social.model.Story { *; }
-keep class com.nidoham.social.model.Reaction { *; }
-keep class com.nidoham.social.model.ReactionType { *; }

# Keep all model constructors
-keepclassmembers class com.nidoham.social.model.** {
    public <init>();
    public <init>(...);
}

# Keep PropertyName annotations
-keepattributes *Annotation*
-keep class com.google.firebase.firestore.PropertyName { *; }

# Firebase Firestore
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep enum classes and their methods
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep data class generated methods
-keepclassmembers class * {
    public ** component1();
    public ** component2();
    public ** component3();
    public ** component4();
    public ** component5();
    public ** copy(...);
}

# ServerTimestamp annotation
-keep class com.google.firebase.firestore.ServerTimestamp { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.ServerTimestamp <fields>;
}