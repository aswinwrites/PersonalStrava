# Room, kotlinx.serialization, and the Supabase/Ktor client all rely on
# reflection/codegen that R8 can mangle if not kept. These rules are the
# standard set for this stack; revisit if release-build crashes look like
# missing-class/serializer issues (check with `adb logcat` after install).

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.personalstrava.app.**$$serializer { *; }
-keepclassmembers class com.personalstrava.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.personalstrava.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
