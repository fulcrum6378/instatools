package ir.mahdiparastesh.instatools.json

import android.net.Uri
import android.os.Handler
import android.text.TextUtils
import android.util.DisplayMetrics
import com.android.volley.*
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Persistent
import java.util.regex.Pattern
import kotlin.reflect.KClass

/** Controls all API interactions with Instagram Web API using Volley and Gson. */
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
    Response.ErrorListener {
        if (it.networkResponse?.statusCode == 500 && it.networkResponse?.data
                ?.let { ba -> String(ba) }?.contains(Login.LOGGED_OUT_MSG_500) == true
            && url != Endpoint.SIGN_OUT.url
        ) {
            c.needAuthentication(); return@ErrorListener; }
        gotError(c, handleError, onError, it)
    }) {

    init {
        if (acc != null) {
            setShouldCache(cache)
            tag = "fetch"
            retryPolicy = DefaultRetryPolicy(
                DEFAULT_TIMEOUT, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
            if (autoQueue) Volley.newRequestQueue(c.c).add(this)
        } else gotError()
    }

    override fun getHeaders(): Map<String, String> =
        Headers(acc!!, method == Method.POST, if (c is BaseActivity) c.dm else null)

    override fun getBody(): ByteArray? = encode(body)?.encodeToByteArray() ?: super.getBody()

    override fun deliverResponse(response: String) {
        val data: JSON? = try {
            Gson().fromJson(response, typeToken ?: clazz.java) as JSON
        } catch (e: JsonSyntaxException) {
            if (response.startsWith("<!DOCTYPE html>")) when {
                url == Endpoint.SIGN_OUT.url -> gotError()
                response.contains("Log in • Instagram") -> {
                    c.needAuthentication()
                    if (c is BaseActivity) gotError()
                }
                response.contains("Content unavailable &bull; Instagram") ->
                    gotError()
                else -> {
                    if (BuildConfig.DEBUG) throw Exception("Couldn't parse $response")
                    else gotError()
                }
            } else {
                if (BuildConfig.DEBUG) throw Exception("Couldn't parse $response")
                else gotError()
            }
            null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) throw Exception("Couldn't parse $response")
            else gotError()
            null
        }
        try {
            data?.let(onSuccess)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) throw e
            else gotError()
        }
    }

    private var nwRes: NetworkResponse? = null
    override fun parseNetworkResponse(response: NetworkResponse): Response<String> {
        nwRes = response
        return Response.success(String(response.data), HttpHeaderParser.parseCacheHeaders(response))
    }

    private fun gotError() {
        nwRes?.apiFailure(c)
        handleError?.obtainMessage(HANDLE_ERROR, nwRes)?.sendToTarget()
        onError?.let { func -> func(nwRes) }
    }

    enum class Endpoint(val url: String) {
        // Profiles
        PROFILE("https://www.instagram.com/api/v1/users/web_profile_info/?username=%s"),
        SEARCH(
            "https://www.instagram.com/api/v1/web/search/topsearch/?context=blended&query=%s" +
                "&include_reel=false&search_surface=web_top_search"
        ), // &rank_token=0.9366187585704904

        // Posts & Stories
        MEDIA_ITEM("https://www.instagram.com/api/v1/media/%s/info/"),
        POSTS(
            "https://www.instagram.com/graphql/query/?query_hash=$postHash" +
                "&variables={\"id\":\"%1\$s\",\"first\":12,\"after\":\"%2\$s\"}"
        ),
        TAGGED("https://www.instagram.com/api/v1/usertags/%1\$s/feed/?count=12&max_id=%2\$s"),
        STORY("https://www.instagram.com/api/v1/feed/user/%s/story/"),
        HIGHLIGHTS("https://www.instagram.com/api/v1/highlights/%s/highlights_tray/"),
        REEL_ITEM("https://www.instagram.com/api/v1/feed/reels_media/?reel_ids=%s"),
        // StoryReel = "Full-Screen Video"; Story { reel, reel, ... }, Highlights { reel, reel, ... }
        // Adding "media_id=" parameter is of no use, the results are the same!!
        /*NEW_TAGGED( // Requires edges again
            "https://www.instagram.com/graphql/query/?query_hash=$taggedHash" +
                    "&variables={\"id\":\"%1\$s\",\"first\":12,\"after\":\"%2\$s\"}"
        ),*///const val taggedHash = "be13233562af2d229b008d2976b998b5"

        // Interactions (always use "?count=" for more accurate results)
        FOLLOWERS("https://www.instagram.com/api/v1/friendships/%1\$s/followers/?count=200&max_id=%2\$s"),
        FOLLOWING("https://www.instagram.com/api/v1/friendships/%1\$s/following/?count=200&max_id=%2\$s"),
        FRIENDSHIPS_MANY("https://www.instagram.com/api/v1/friendships/show_many/"), /*
        // method = POST, "user_ids=<ids separated by ",">", expect Rest$Friendships *//*
        FRIENDSHIP("https://www.instagram.com/api/v1/friendships/show/%s/"), // GET */
        FOLLOW("https://www.instagram.com/api/v1/friendships/create/%s/"),
        UNFOLLOW("https://www.instagram.com/api/v1/friendships/destroy/%s/"),
        /*MUTE("https://www.instagram.com/api/v1/friendships/mute_posts_or_story_from_follow/"),
        UNMUTE("https://www.instagram.com/api/v1/friendships/unmute_posts_or_story_from_follow/"),
        // method = POST, "target_posts_author_id=<USER_ID>" AND(using &)/OR "target_reel_author_id=<USER_ID>",
        // expect Rest$Friendships*/
        /*RESTRICT("https://www.instagram.com/api/v1/web/restrict_action/restrict/"),
        UNRESTRICT("https://www.instagram.com/api/v1/web/restrict_action/unrestrict/"),
        // method = POST, body = "target_user_id=<USER_ID>", expect "{"status":"ok"}" */
        /*BLOCK("https://www.instagram.com/api/v1/web/friendships/%d/block/"),
        UNBLOCK("https://www.instagram.com/api/v1/web/friendships/%d/unblock/"),
        // method = POST, expect "{"status":"ok"}" */

        // Saving
        SAVED("https://www.instagram.com/api/v1/feed/saved/posts/"),
        UNSAVE("https://www.instagram.com/web/save/%s/unsave/"),
        //SAVE("https://www.instagram.com/web/save/%s/save/"),
        // The fucking web API used /web/save for fulcrum6378 and /graphql/query for instatools.apk !?!

        // Messaging
        INBOX("https://www.instagram.com/api/v1/direct_v2/inbox/?cursor=%s"),
        DIRECT("https://www.instagram.com/api/v1/direct_v2/threads/%1\$s/?cursor=%2\$s&limit=%3\$d"),/*
        // persistentBadging=true&folder=[0(PRIMARY)|1(GENERAL)]
        // Avoiding "limit" argument will default to 20, but can be more than that. */
        SEEN("https://www.instagram.com/api/v1/direct_v2/threads/%1\$s/items/%2\$s/seen/"),

        // Logging in/out
        SIGN_OUT("https://www.instagram.com/accounts/logout/ajax/"),// MEDIA_ITEM

        RAW_QUERY("https://www.instagram.com/graphql/query"),
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        const val HANDLE_ERROR = 100
        const val postHash = "8c2a529969ee035a5063f2fc8602a0fd"
        const val DEFAULT_TIMEOUT = 15000

        @Suppress("SpellCheckingInspection")
        fun graphQlBody(cnfWrapper: PageConfig, shortcode: String): String {
            val siteData = cnfWrapper.define["SiteData"]!![1] as Map<String, Any>
            return "access_token=" +
                "&__d=" + siteData["haste_site"] +
                "&__user=0" +
                "&__a=1" +
                "&__dyn=7xeUmwlE7ibwKBWo2vwAxu13w8CewSwMwNw9G2S0lW4o0B-q1ew65xO0F" +
                "E2awt81sbzoaEd82lwv89k2C1Fwc61uwZx-0z8jwae4UaEW0D888cobEaU2eUlwh" +
                "E2Lx_w4HwJwSyES1Twoob82ZwiU8UdUbGwbO1pw" /*TODO*/ +
                "&__csr=glhcrillJsB9N5GL8F6LV9lGm4oSAZUOVoCimE8ideXGXAgynCF5KEy2y" +
                "00gc905eyRc02JG3C4m4o7y0zyw4Za2ye3ywXm3O6204pjgYwKoEy2u7u1RwjlG0" +
                "j10PwbZ0ww15Kbm0oK0YU" /*TODO*/ +
                "&__req=3" /*TODO d or 3?*/ +
                "&__hs=" + siteData["haste_session"] +
                "&dpr=1" +
                "&__ccg=" + (cnfWrapper.define["WebConnectionClassServerGuess"]!![1]
                as Map<String, String>)["connectionClass"]!! +
                "&__rev=" + (siteData["client_revision"] as Double)
                .toInt().toString() +
                "&__s=eiw83y%3Aude3gw%3Ap6j381" /*TODO*/ +
                "&__hsi=" + siteData["haste_session"] +
                "&__comet_req=7" +
                "&fb_dtsg=" + (cnfWrapper.define["DTSGInitialData"]!![1]
                as Map<String, String>)["token"]!! + // or DTSGInitData and async_get_token
                "&jazoest=26314" /*TODO 26314 or 26301*/ +
                "&lsd=" + (cnfWrapper.define["LSD"]!![1] as Map<String, String>)["token"]!! +
                "&__spin_r=" + (siteData["__spin_r"] as Double).toInt() +
                "&__spin_b=" + siteData["__spin_b"] +
                "&__spin_t=" + (siteData["__spin_t"] as Double).toInt() +
                "&fb_api_caller_class=RelayModern" +
                "&fb_api_req_friendly_name=PolarisPostRootQuery" +
                /*TODO usePolarisSaveMediaSaveMutation or PolarisPostRootQuery*/
                "&variables=%7B%22shortcode%22%3A%22$shortcode%22%7D" /*TODO shortcode or media id?!?*/ +
                "&server_timestamps=true" +
                "&doc_id=18086740648321782" /*TODO*/
        }

        fun gotError(
            c: Persistent, handleError: Handler?, onError: ((res: NetworkResponse?) -> Unit)?,
            res: VolleyError? = null, msgWhat: Int = HANDLE_ERROR
        ) {
            res?.networkResponse?.apiFailure(c)
            handleError?.obtainMessage(msgWhat, res?.networkResponse)?.sendToTarget()
            onError?.let { func -> func(res?.networkResponse) }
        }

        fun NetworkResponse.apiFailure(c: Persistent) {
            if (statusCode == 400) try {
                if (Gson().fromJson(String(data), Rest.ApiFailure::class.java).lock)
                    c.needAuthentication()
            } catch (_: JsonSyntaxException) {
            }
        }

        fun encode(uriString: String?): String? {
            if (uriString == null) return null
            if (TextUtils.isEmpty(uriString)) return uriString
            val allowedUrlCharacters = Pattern.compile(
                "([A-Za-z\\d_.~:/?#\\[\\]@!$&'()*+,;" + "=-]|%[\\da-fA-F]{2})+"
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

        /** Helper class for adding a Request to a RequestQueue in Volley. */
        var RequestQueue.adder: Request<*>?
            get() = null
            set(req) {
                add(req)
            }
    }

    /** Controls all HTTP headers. */
    @Suppress("SpellCheckingInspection")
    class Headers(acc: Account, isImperative: Boolean = false, dm: DisplayMetrics? = null) :
        HashMap<String, String>() {
        init {
            this["accept-language"] = "en-US"
            this["sec-ch-ua"] = "\" Not;A Brand\";\"InstaTools\""
            this["sec-ch-ua-mobile"] = "?1"
            this["sec-ch-ua-platform"] = "\"InstaTools - Android\""
            this["sec-fetch-dest"] = "empty"
            this["sec-fetch-mode"] = "cors"
            this["sec-fetch-site"] = "same-origin"
            if (dm != null) this["viewport-width"] =
                (dm.widthPixels / dm.density).toInt().toString()
            // "cache-control": "max-age=0" // SET THIS IN ORDER TO DISABLE CACHE

            val cookies = acc.cook ?: ""
            if (isImperative) {
                this["content-type"] = "application/x-www-form-urlencoded"
                this["x-requested-with"] = "XMLHttpRequest"
                if (acc.roll != null) this["x-instagram-ajax"] = acc.roll!!
                /*"x-fb-friendly-name": "usePolarisSaveMediaSaveMutation",
    "x-fb-lsd": "4XmgR5VLJlf9HL7hUQOtwn",*/// IS IT NECESSARY FOR RAW_QUERY?!?
            } else { // Cookie "rur" is different between MEDIA_ITEM and GET but the same between themselves
                this["accept"] = "*/*"
            }
            if (cookies.contains("csrftoken=")) this["x-csrftoken"] =
                cookies.substringAfter("csrftoken=").substringBefore(";")
            /* For this, load "https://www.instagram.com/static/bundles/es6/ConsumerLibCommons.js/5bb0ab377d4d.js"
             * Substring after "e.ASBD_ID='", substring before "'" */
            this["x-asbd-id"] = "198387" // STATIC
            // this["x-ig-www-claim"] = "hmac.AR1HhBJvtNorxBvZdmf8jZXs1JfsT2WhmwcKgtdyoYXsHCws"
            this["x-ig-app-id"] = "936619743392459" // STATIC
            this["cookie"] = cookies
            this["Referer"] = "https://www.instagram.com/"
            this["Referrer-Policy"] = "strict-origin-when-cross-origin"

            // Added myself
            /*this["Access-Control-Allow-Origin"] = "https://www.instagram.com/"
            this["Access-Control-Allow-Credentials"] = "true"*/
        }
    }
}
