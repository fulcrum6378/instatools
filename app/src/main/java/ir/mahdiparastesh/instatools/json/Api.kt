package ir.mahdiparastesh.instatools.json

import android.net.Uri
import android.text.TextUtils
import android.widget.Toast
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.more.BaseActivity
import java.util.regex.Pattern

class Api<JSON>(
    val c: BaseActivity,
    url: String,
    private val clazz: Class<*>,
    private val body: String? = null,
    cache: Boolean = false,
    method: Int = Method.GET,
    private val listener: (json: JSON) -> Unit,
) : Request<String>(method, encode(url), Response.ErrorListener {
    Toast.makeText(c, "ERROR: ${it.networkResponse.statusCode}", Toast.LENGTH_LONG).show()
}) {

    init {
        setShouldCache(cache)
        tag = "fetch"
        retryPolicy = DefaultRetryPolicy(
            10000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        Volley.newRequestQueue(c).add(this)
    }

    override fun getHeaders(): HashMap<String, String> = Headers(c)

    override fun getBody(): ByteArray = encode(body)?.encodeToByteArray() ?: super.getBody()

    @Suppress("UNCHECKED_CAST")
    override fun deliverResponse(response: String) = listener(Gson().fromJson(response, clazz) as JSON)

    override fun parseNetworkResponse(response: NetworkResponse): Response<String> =
        Response.success(String(response.data), HttpHeaderParser.parseCacheHeaders(response))

    enum class Type(val url: String) {
        FOLLOWERS("https://i.instagram.com/api/v1/friendships/%1\$s/followers/?max_id=%2\$s"),
        FOLLOWING("https://i.instagram.com/api/v1/friendships/%1\$s/following/?max_id=%2\$s"),
        FRIENDSHIPS("https://i.instagram.com/api/v1/friendships/show_many/"),

        PROFILE("https://www.instagram.com/%s/?__a=1"),
        POSTS(
            "https://www.instagram.com/graphql/query/?query_hash=$postHash" +
                    "&variables={\"id\":\"%1\$s\",\"first\":%2\$s,\"after\":\"%3\$s\"}"
        ),
        SAVED_FIRST("https://www.instagram.com/%s/saved/?__a=1"),
        SAVED("https://www.instagram.com/graphql/query/?query_hash=$savedHash" +
                "&variables={\"id\":\"%1\$s\",\"first\":%2\$s,\"after\":\"%3\$s\"}")
        // Both give a GraphQlResponse
    }

    companion object {
        const val postHash = "8c2a529969ee035a5063f2fc8602a0fd"
        const val savedHash = "2ce1d673055b99250e93b6f88f878fde"

        fun encode(uriString: String?): String? {
            if (uriString == null) return null
            if (TextUtils.isEmpty(uriString)) return uriString
            val allowedUrlCharacters = Pattern.compile(
                "([A-Za-z0-9_.~:/?\\#\\[\\]@!$&'()*+,;" + "=-]|%[0-9a-fA-F]{2})+"
            )
            val matcher = allowedUrlCharacters.matcher(uriString)
            var validUri: String? = null
            if (matcher.find()) validUri = matcher.group()
            if (TextUtils.isEmpty(validUri) || uriString.length == validUri!!.length)
                return uriString

            val uri = Uri.parse(uriString)
            val uriBuilder = Uri.Builder().scheme(uri.scheme).authority(uri.authority)
            for (path in uri.pathSegments) uriBuilder.appendPath(path)
            for (key in uri.queryParameterNames)
                uriBuilder.appendQueryParameter(key, uri.getQueryParameter(key))
            return uriBuilder.build().toString()
        }
    }

    @Suppress("SpellCheckingInspection")
    class Headers(c: BaseActivity) : HashMap<String, String>() {
        init {
            this["accept"] = "*/*"
            this["accept-language"] = "en-GB"
            this["sec-ch-ua"] = "\" Not;A Brand\";\"InstaTools\""
            this["sec-ch-ua-mobile"] = "?1"
            this["sec-ch-ua-platform"] = "\"InstaTools - Android\""
            this["sec-fetch-dest"] = "empty"
            this["sec-fetch-mode"] = "cors"
            this["sec-fetch-site"] = "same-origin"
            this["x-requested-with"] = "XMLHttpRequest"

            // The rest are dynamic
            //this["x-asbd-id"] = "198387"
            //this["x-csrftoken"] = csrfToken
            //this["x-ig-www-claim"] = "hmac.AR1HhBJvtNorxBvZdmf8jZXs1JfsT2WhmwcKgtdyoYXsHCnL"
            this["x-ig-app-id"] = "936619743392459"
            this["cookie"] = c.sp.getString(Login.spCookies.format(c.m.acc.id), "") ?: ""
            //this["Referer"] = "https://www.instagram.com/fulcrum1378/saved/"
            //this["Referrer-Policy"] = "strict-origin-when-cross-origin"
        }
    }
}
