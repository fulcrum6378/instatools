-keep class ir.mahdiparastesh.instatools.data.Account { <fields>; }
-keep class ir.mahdiparastesh.instatools.data.Exportable$Options { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.Audible { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.Dm { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.Dm$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.GraphQl { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.GraphQl$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.Media { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.Media$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.PageConfig { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.PageConfig$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.Rest { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.Rest$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.json.Versioned { <fields>; }

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# required for JavaScript interface of WebView
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# required for the ChipsLayoutManager library
-dontwarn ir.mahdiparastesh.chlm.Orientation
-dontwarn javax.annotation.Nullable
