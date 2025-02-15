package ir.mahdiparastesh.instatools.json

import android.net.Uri
import android.text.TextUtils
import android.util.DisplayMetrics
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import ir.mahdiparastesh.instatools.data.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.util.regex.Pattern
import javax.net.ssl.HttpsURLConnection
import kotlin.reflect.KClass

/** Controls all API interactions with Instagram Web API using Volley and Gson. */
object Api {
    var cookies = ""

    /**
     * @return JSON on success, null if the procedure fails
     */
    @WorkerThread
    suspend fun <JSON> call(
        url: String,
        clazz: KClass<*>,
        generics: Array<KClass<*>>? = null,
        isPost: Boolean = false, // TODO merge with `body` if possible
        body: String? = null,
        retry: Int = 1, // TODO implement retrying
        cache: Boolean = false,
        @MainThread onError: ((code: Int) -> Unit)? = null
    ): JSON? {
        if (cookies == "") return null

        val con = URI(url).toURL().openConnection() as HttpsURLConnection
        con.requestMethod = if (isPost) "POST" else "GET"
        con.setRequestProperty("x-asbd-id", "129477")
        if (cookies.contains("csrftoken=")) con.setRequestProperty(
            "x-csrftoken",
            cookies.substringAfter("csrftoken=").substringBefore(";")
        )
        con.setRequestProperty("x-ig-app-id", "936619743392459")
        con.setRequestProperty("cookie", cookies)
        if (isPost && body != null) {
            con.doOutput = true
            con.setRequestProperty("content-type", "application/x-www-form-urlencoded")
        }
        con.useCaches = cache
        con.connectTimeout = 8000
        con.doInput = true
        con.readTimeout = 10000
        try {
            con.connect()
        } catch (_: SocketTimeoutException) {
            if (onError != null) withContext(Dispatchers.Main) { onError(-1) }
            return null
        }

        if (isPost && body != null)
            con.outputStream.bufferedWriter().use { it.write(body) }

        val text = try {
            con.inputStream.bufferedReader().readText()
        } catch (_: IOException) {
            if (onError != null) withContext(Dispatchers.Main) { onError(-2) }
            return null
        }

        return if (con.responseCode == 200) try {
            Gson().fromJson(
                text,
                if (generics != null) TypeToken.getParameterized(
                    clazz.java, *generics.map { it.java }.toTypedArray()
                ).type else clazz.java
            ) as JSON
        } catch (_: JsonSyntaxException) {
            if (onError != null) withContext(Dispatchers.Main) { onError(-3) }
            null
        } else {
            if (onError != null) withContext(Dispatchers.Main) { onError(con.responseCode) }
            null
        }
    }
    /*Response.ErrorListener {
    if (it.networkResponse?.statusCode == 500 && it.networkResponse?.data
            ?.let { ba -> String(ba) }?.contains(Login.LOGGED_OUT_MSG_500) == true
        && url != Endpoint.SIGN_OUT.url && !neverMindIfNeedAuth
    ) {
        c.needAuthentication(); return@ErrorListener; }
    gotError(c, handleError, onError, it, neverMindIfNeedAuth = neverMindIfNeedAuth)
}*/
    /*if (response.startsWith("<!DOCTYPE html>")) when {
            url == Endpoint.SIGN_OUT.url -> gotError()
            response.contains("Login • Instagram") -> {
                neverMindIfNeedAuth
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
        }*/

    /**
     * @return String (HTML) on success, null if the procedure fails
     */
    @WorkerThread
    suspend fun page(
        url: String,
        @MainThread onError: ((code: Int) -> Unit)? = null
    ): String? {
        val con = URI(url).toURL().openConnection() as HttpsURLConnection
        con.requestMethod = "GET"
        con.setRequestProperty("accept", "text/html")
        con.setRequestProperty(
            "user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/133.0.0.0 Safari/537.36"
        )
        con.setRequestProperty("cookie", cookies)
        con.useCaches = false
        con.connectTimeout = 8000
        con.doInput = true
        con.readTimeout = 12000
        try {
            con.connect()
        } catch (_: SocketTimeoutException) {
            if (onError != null) withContext(Dispatchers.Main) { onError(-1) }
            return null
        }

        return if (con.responseCode == 200) try {
            con.inputStream.bufferedReader().readText()
        } catch (_: IOException) {
            if (onError != null) withContext(Dispatchers.Main) { onError(-2) }
            null
        } else {
            if (onError != null) withContext(Dispatchers.Main) { onError(con.responseCode) }
            null
        }
    }

    fun error(status: Int) = when (status) {
        -1 -> "Couldn't connect to Instagram!"
        -2 -> "Connection was broken!"
        -3 -> "Invalid response from Instagram!"
        302 -> "Found redirection!"
        401 -> "You've been logged out!"
        404 -> "Not found!"
        429 -> "Too many requests!"
        else -> "HTTP error code $status!"
    } // TODO create string resources

    enum class Endpoint(val url: String) {
        // Profiles
        PROFILE("https://www.instagram.com/api/v1/users/web_profile_info/?username=%s"),
        INFO("https://www.instagram.com/api/v1/users/%s/info/"),
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

        //FOLLOW("https://www.instagram.com/api/v1/friendships/create/%s/"),
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
    const val HANDLE_ERROR = 100
    const val postHash = "8c2a529969ee035a5063f2fc8602a0fd"

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
