package ir.mahdiparastesh.instatools.json

import android.net.Uri
import android.os.Handler
import android.text.TextUtils
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.more.Persistent
import java.util.regex.Pattern
import kotlin.reflect.KClass

class Api<JSON>(
    val c: Persistent,
    url: String,
    private val clazz: KClass<*>,
    private val handleError: Handler?,
    private val body: String? = null,
    cache: Boolean = false,
    method: Int = Method.GET,
    onError: ((res: NetworkResponse?) -> Unit)? = null,
    private val onSuccess: (json: JSON) -> Unit
) : Request<String>(method, encode(url), Response.ErrorListener {
    handleError?.obtainMessage(HANDLE_ERROR, it.networkResponse)?.sendToTarget() // NetworkResponse?
    onError?.let { func -> func(it.networkResponse) }
}) {
    init {
        setShouldCache(cache)
        tag = "fetch"
        retryPolicy = DefaultRetryPolicy(
            10000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        Volley.newRequestQueue(c.c).add(this)
    }

    override fun getHeaders(): Map<String, String> = Headers(c.m.acc!!, method == Method.POST)

    override fun getBody(): ByteArray? = encode(body)?.encodeToByteArray() ?: super.getBody()

    @Suppress("UNCHECKED_CAST")
    override fun deliverResponse(response: String) {
        try {
            onSuccess(Gson().fromJson(response, clazz.java) as JSON)
        } catch (e: JsonSyntaxException) {
            if (response.startsWith("<!DOCTYPE html>", true)
                && response.contains("Log in • Instagram")
            ) {
                // TODO
            } else throw Exception(response)
        }
    }

    override fun parseNetworkResponse(response: NetworkResponse): Response<String> =
        Response.success(String(response.data), HttpHeaderParser.parseCacheHeaders(response))

    @Suppress("unused")
    enum class Type(val url: String) {
        PROFILE("https://www.instagram.com/%s/?__a=1"),
        POSTS(
            "https://www.instagram.com/graphql/query/?query_hash=$postHash" +
                    "&variables={\"id\":\"%1\$s\",\"first\":%2\$s,\"after\":\"%3\$s\"}"
        ),
        REELS("https://i.instagram.com/api/v1/feed/reels_media/?reel_ids=%s"),
        POST("https://www.instagram.com/p/%s/?__a=1"),
        SEARCH("https://www.instagram.com/web/search/topsearch/?context=user&query=%s"),

        FOLLOWERS("https://i.instagram.com/api/v1/friendships/%1\$s/followers/?max_id=%2\$s"),
        FOLLOWING("https://i.instagram.com/api/v1/friendships/%1\$s/following/?max_id=%2\$s"),
        FRIENDSHIPS("https://i.instagram.com/api/v1/friendships/show_many/"),
        FOLLOW("https://www.instagram.com/web/friendships/%s/follow/"),
        UNFOLLOW("https://www.instagram.com/web/friendships/%s/unfollow/"),

        SAVED_FIRST("https://www.instagram.com/%s/saved/?__a=1"),
        SAVED(
            "https://www.instagram.com/graphql/query/?query_hash=$savedHash" +
                    "&variables={\"id\":\"%1\$s\",\"first\":%2\$s,\"after\":\"%3\$s\"}"
        ),
        SAVE("https://www.instagram.com/web/save/%s/save/"),
        UNSAVE("https://www.instagram.com/web/save/%s/unsave/"),

        INBOX("https://i.instagram.com/api/v1/direct_v2/inbox/?cursor=%s"),

        //persistentBadging=true&folder=[0(PRIMARY)|1(GENERAL)]&limit=10
        DIRECT("https://i.instagram.com/api/v1/direct_v2/threads/%1\$s/?cursor=%2\$s"),

        SIGN_OUT("https://www.instagram.com/accounts/logout/ajax/")// POST
    }

    companion object {
        const val HANDLE_ERROR = 100
        const val postHash = "8c2a529969ee035a5063f2fc8602a0fd"
        const val savedHash = "2ce1d673055b99250e93b6f88f878fde"

        fun encode(uriString: String?): String? {
            if (uriString == null) return null
            if (TextUtils.isEmpty(uriString)) return uriString
            val allowedUrlCharacters = Pattern.compile(
                "([A-Za-z0-9_.~:/?#\\[\\]@!$&'()*+,;" + "=-]|%[0-9a-fA-F]{2})+"
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
    class Headers(acc: Account, isImperative: Boolean = false) : HashMap<String, String>() {
        init {
            this["accept"] = "*/*"
            this["accept-language"] = "en-GB"
            this["sec-ch-ua"] = "\" Not;A Brand\";\"InstaTools\""
            this["sec-ch-ua-mobile"] = "?1"
            this["sec-ch-ua-platform"] = "\"InstaTools - Android\""
            this["sec-fetch-dest"] = "empty"
            this["sec-fetch-mode"] = "cors"
            // "cache-control": "max-age=0" // SET THIS IN ORDER TO DISABLE CACHE

            val cookies = acc.cook ?: ""
            if (isImperative) {
                this["content-type"] = "application/x-www-form-urlencoded"
                this["sec-fetch-site"] = "same-origin"
                this["x-requested-with"] = "XMLHttpRequest"
                if (cookies.contains("csrftoken="))
                    this["x-csrftoken"] = cookies
                        .substringAfter("csrftoken=")
                        .substringBefore(";")
                //this["x-instagram-ajax"] = "7f7346b22318"
                //"Referer": "https://www.instagram.com/instagram/",
            } else { // Cookie "rur" is different between POST and GET but the same between themselves
                this["sec-fetch-site"] = "same-site"
            }
            //this["x-asbd-id"] = "198387"
            //this["x-ig-www-claim"] = "hmac.AR1HhBJvtNorxBvZdmf8jZXs1JfsT2WhmwcKgtdyoYXsHCws"
            this["x-ig-app-id"] = "936619743392459"
            this["cookie"] = cookies
            this["Referer"] = "https://www.instagram.com/"
            this["Referrer-Policy"] = "strict-origin-when-cross-origin"

            // Added myself
            this["Access-Control-Allow-Origin"] = "https://www.instagram.com/"
            this["Access-Control-Allow-Credentials"] = "true"
        }
    }
}
