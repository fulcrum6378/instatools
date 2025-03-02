-keep class ir.mahdiparastesh.instatools.data.Account { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.GraphQl { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.GraphQl$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Media { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Media$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Rest { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Rest$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.User { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.User$* { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Story { <fields>; }
-keep class ir.mahdiparastesh.instatools.api.Story$* { <fields>; }

# required for JavaScript interface of WebView
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
