package ir.mahdiparastesh.instatools.json

import android.net.Uri
import android.os.Handler
import android.text.TextUtils
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.ForegroundService
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
    private val acc: Account? = c.m.acc,
    private val typeToken: java.lang.reflect.Type? = null,
    autoQueue: Boolean = true,
    private val onError: ((res: NetworkResponse?) -> Unit)? = null,
    private val onSuccess: (json: JSON) -> Unit
) : Request<String>(method, encode(url),
    Response.ErrorListener { gotError(handleError, onError, it) }) {

    init {
        if (acc != null) {
            setShouldCache(cache)
            tag = "fetch"
            retryPolicy = DefaultRetryPolicy(
                15000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
            if (autoQueue) Volley.newRequestQueue(c.c).add(this)
        } else gotError(handleError, onError)
    }

    override fun getHeaders(): Map<String, String> = Headers(acc!!, method == Method.POST)

    override fun getBody(): ByteArray? = encode(body)?.encodeToByteArray() ?: super.getBody()

    @Suppress("UNCHECKED_CAST")
    override fun deliverResponse(response: String) {
        val data: JSON? = try {
            Gson().fromJson(response, typeToken ?: clazz.java) as JSON
        } catch (e: JsonSyntaxException) {
            if (response.startsWith("<!DOCTYPE html>")) when {
                url == Endpoint.SIGN_OUT.url -> gotError(this)
                response.contains("Log in • Instagram") -> {
                    ForegroundService.terminateTasks(c.c)
                    c.gsp.edit().remove(Login.spAccount).apply()
                    c.needAuthentication()
                    if (c is BaseActivity) gotError(this)
                }
                response.contains("Content unavailable &bull; Instagram") ->
                    gotError(this)
                else -> {
                    if (BuildConfig.DEBUG) throw e else gotError(this)
                }
            } else {
                if (BuildConfig.DEBUG) throw e else gotError(this)
            }
            null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) throw e else gotError(this)
            null
        }
        try {
            data?.let(onSuccess)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) throw e
            else gotError(this)
        }
    }

    var nwRes: NetworkResponse? = null
    override fun parseNetworkResponse(response: NetworkResponse): Response<String> {
        nwRes = response
        return Response.success(String(response.data), HttpHeaderParser.parseCacheHeaders(response))
    }

    enum class Endpoint(val url: String) {
        PROFILE("https://i.instagram.com/api/v1/users/web_profile_info/?username=%s"),
        MEDIA_ITEM("https://i.instagram.com/api/v1/media/%s/info/"),

        //INFO("https://i.instagram.com/api/v1/users/%s/info/"),
        POSTS(
            "https://www.instagram.com/graphql/query/?query_hash=$postHash" +
                    "&variables={\"id\":\"%1\$s\",\"first\":%2\$s,\"after\":\"%3\$s\"}"
        ),
        TAGGED("https://i.instagram.com/api/v1/usertags/%1\$s/feed/?count=12&max_id=%2\$s"),
        STORY("https://i.instagram.com/api/v1/feed/user/%s/story/"),
        HIGHLIGHTS("https://i.instagram.com/api/v1/highlights/%s/highlights_tray/"),
        REEL_ITEM("https://i.instagram.com/api/v1/feed/reels_media/?reel_ids=%s"),
        // StoryReel = "Full-Screen Video"; Story { reel, reel, ... }, Highlights { reel, reel, ... }

        FOLLOWERS("https://i.instagram.com/api/v1/friendships/%1\$s/followers/?max_id=%2\$s"),
        FOLLOWING("https://i.instagram.com/api/v1/friendships/%1\$s/following/?max_id=%2\$s"),

        //FRIENDSHIPS("https://i.instagram.com/api/v1/friendships/show_many/"),
        FOLLOW("https://www.instagram.com/web/friendships/%s/follow/"),
        UNFOLLOW("https://www.instagram.com/web/friendships/%s/unfollow/"),

        SAVED(
            "https://www.instagram.com/graphql/query/?query_hash=$savedHash" +
                    "&variables={\"id\":\"%1\$s\",\"first\":%2\$s,\"after\":\"%3\$s\"}"
        ),// This method brings posts with large thumbnails and no other candidates

        //SAVE("https://www.instagram.com/web/save/%s/save/"),
        UNSAVE("https://www.instagram.com/web/save/%s/unsave/"),

        INBOX("https://i.instagram.com/api/v1/direct_v2/inbox/?cursor=%s"),
        DIRECT("https://i.instagram.com/api/v1/direct_v2/threads/%1\$s/?cursor=%2\$s&limit=%3\$d"),
        //persistentBadging=true&folder=[0(PRIMARY)|1(GENERAL)]
        // Avoiding "limit" argument will default to 20, but can be more than that.

        SEARCH("https://www.instagram.com/web/search/topsearch/?context=user&query=%s"),

        SIGN_OUT("https://www.instagram.com/accounts/logout/ajax/")// MEDIA_ITEM
    }

    companion object {
        const val HANDLE_ERROR = 100
        const val postHash = "8c2a529969ee035a5063f2fc8602a0fd"
        const val savedHash = "2ce1d673055b99250e93b6f88f878fde"

        fun <JSON> gotError(api: Api<JSON>, res: NetworkResponse? = api.nwRes) {
            api.handleError?.obtainMessage(HANDLE_ERROR, res)?.sendToTarget()
            api.onError?.let { func -> func(res) }
        }

        fun gotError(
            handleError: Handler?, onError: ((res: NetworkResponse?) -> Unit)?,
            res: VolleyError? = null
        ) {
            handleError?.obtainMessage(HANDLE_ERROR, res?.networkResponse)?.sendToTarget()
            onError?.let { func -> func(res?.networkResponse) }
        }

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

        var RequestQueue.adder: Request<*>?
            get() = null
            set(req) {
                add(req)
            }
    }

    @Suppress("SpellCheckingInspection")
    class Headers(acc: Account, isImperative: Boolean = false) :
        HashMap<String, String>() {
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
                if (acc.roll != null) this["x-instagram-ajax"] = acc.roll!!
            } else { // Cookie "rur" is different between MEDIA_ITEM and GET but the same between themselves
                this["sec-fetch-site"] = "same-site"
            }
            if (cookies.contains("csrftoken="))
                this["x-csrftoken"] = cookies
                    .substringAfter("csrftoken=")
                    .substringBefore(";")
            this["x-asbd-id"] = "198387" // MIGHT BE THE SAME FOR DIFFERENT ACCOUNTS
            // For ^, load "https://www.instagram.com/static/bundles/es6/ConsumerLibCommons.js/5bb0ab377d4d.js"
            // Substring after "e.ASBD_ID='", substring before "'"
            // this["x-ig-www-claim"] = "hmac.AR1HhBJvtNorxBvZdmf8jZXs1JfsT2WhmwcKgtdyoYXsHCws"
            // But this one is NOT
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
