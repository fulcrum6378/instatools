-keep class ir.mahdiparastesh.instatools.data.Account { <fields>; }
-keep class ir.mahdiparastesh.instatools.data.Exportable$Options { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Dm { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Dm$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.GraphQl { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.GraphQl$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Media { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Media$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.PageConfig { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.PageConfig$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Rest { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Rest$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.User { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.User$* { <fields>; }

# required for JavaScript interface of WebView
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# required for the ChipsLayoutManager library
-dontwarn ir.mahdiparastesh.chlm.Orientation
-dontwarn javax.annotation.Nullable
