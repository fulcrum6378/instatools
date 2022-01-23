package ir.mahdiparastesh.instatools.json

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import java.util.regex.Pattern

class Api<JSON>(
    c: Context,
    url: String,
    private val clazz: Class<*>,
    private val body: String? = null,
    cache: Boolean = false,
    method: Int = Method.GET,
    private val listener: (json: JSON) -> Unit,
) : Request<String>(method, encode(url), Response.ErrorListener {}) {

    init {
        setShouldCache(cache)
        tag = "fetch"
        retryPolicy = DefaultRetryPolicy(
            10000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        Volley.newRequestQueue(c).add(this)
    }

    override fun getHeaders(): HashMap<String, String> = Headers()

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
            "https://www.instagram.com/graphql/query/?query_hash=%1\$s&variables=" +
                    "{\"id\":\"%2\$s\",\"first\":%3\$s,\"after\":\"%4\$s\"}"
        ),
    }

    companion object {
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

    class Headers : HashMap<String, String>() {
        init {
            this["accept"] = "*/*"
            this["accept-language"] = "en-GB,en;q=0.9,fa-IR;q=0.8,fa;q=0.7,en-US;q=0.6"
            this["sec-ch-ua"] =
                "\" Not;A Brand\";v=\"99\", \"Google Chrome\";v=\"97\", \"Chromium\";v=\"97\""
            this["sec-ch-ua-mobile"] = "?0"
            this["sec-ch-ua-platform"] = "\"Windows\""
            this["sec-fetch-dest"] = "empty"
            this["sec-fetch-mode"] = "cors"
            this["sec-fetch-site"] = "same-origin"
            this["x-asbd-id"] = "198387"
            this["x-csrftoken"] = "muR5txc62fJJgFLDJGegf7wqqHqNK9Nc"
            this["x-ig-app-id"] = "936619743392459"
            this["x-ig-www-claim"] = "hmac.AR1HhBJvtNorxBvZdmf8jZXs1JfsT2WhmwcKgtdyoYXsHP-m"
            this["x-requested-with"] = "XMLHttpRequest"
            this["cookie"] = "mid=Ydl99AALAAEct5iPgnTvK4heOIeK; " +
                    "ig_did=A2BEADD7-5EF3-4246-B802-AE610784961B; " +
                    "ig_nrcb=1; csrftoken=muR5txc62fJJgFLDJGegf7wqqHqNK9Nc; " +
                    "ds_user_id=8337021434; " +
                    "sessionid=8337021434%3AU05zzgOUBMZoL8%3A12; " +
                    "shbid=\"14488\\0548337021434\\0541674418611:01f7313f3fca98d5711465a77b9d7230db9424bbe4b667e86a636f637bc4e0c36bd5987b\"; " +
                    "shbts=\"1642882611\\0548337021434\\0541674418611:01f76cc04470913fab4d38b796adf6d273f2d076a2da911cf3f69d420971dd6a920a2855\"; " +
                    "rur=\"RVA\\0548337021434\\0541674428566:01f792a638d7df819ac81b122eb461173cc82911101b6e0917d0879c0f30691441cb501b\""
            //this["Referer"] = "https://www.instagram.com/coseluccicose/"
            //this["Referrer-Policy"] = "strict-origin-when-cross-origin"
        }
    }
}
