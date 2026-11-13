# Conservative R8 setup: shrink only, never rename, so reflection keeps working.
-dontobfuscate

# Keep all of PantryPulse's own code intact.
-keep class com.pantrypulse.app.** { *; }

# osmdroid loads tile providers and overlays reflectively.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Room, WorkManager, DataStore, CameraX and ML Kit ship their own consumer rules.
