# RoadSOS release rules.
#
# Gson reads and writes the on-device JSON database by reflection, so the model
# field names have to survive R8. Everything else is fair game for shrinking.
-keepclassmembers class com.roadsos.app.data.** { <fields>; }
-keepclassmembers class com.roadsos.app.engine.DbState { <fields>; }
-keepclassmembers class com.roadsos.app.engine.StoredIncident { <fields>; }
-keepclassmembers class com.roadsos.app.engine.Responder { <fields>; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep generic signatures used by Gson's TypeToken in Store.
-keepattributes Signature
-keepattributes *Annotation*

# Kotlin metadata is not needed at runtime.
-dontwarn kotlin.Metadata
