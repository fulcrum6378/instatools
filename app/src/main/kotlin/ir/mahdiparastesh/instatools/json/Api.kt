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

@Suppress("UNCHECKED_CAST")
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
        PROFILE("https://i.instagram.com/api/v1/users/web_profile_info/?username=%s"),
        SEARCH("https://www.instagram.com/web/search/topsearch/?context=user&query=%s"),

        // Posts & Stories
        MEDIA_ITEM("https://i.instagram.com/api/v1/media/%s/info/")
        /*INFO("https://i.instagram.com/api/v1/users/%s/info/"),*/,
        POSTS(
            "https://www.instagram.com/graphql/query/?query_hash=$postHash" +
                    "&variables={\"id\":\"%1\$s\",\"first\":%2\$s,\"after\":\"%3\$s\"}"
        ),
        TAGGED("https://i.instagram.com/api/v1/usertags/%1\$s/feed/?count=12&max_id=%2\$s"),
        STORY("https://i.instagram.com/api/v1/feed/user/%s/story/"),
        HIGHLIGHTS("https://i.instagram.com/api/v1/highlights/%s/highlights_tray/"),
        REEL_ITEM("https://i.instagram.com/api/v1/feed/reels_media/?reel_ids=%s"),
        // StoryReel = "Full-Screen Video"; Story { reel, reel, ... }, Highlights { reel, reel, ... }
        // Adding "media_id=" parameter is of no use, the results are the same!!

        // Interactions
        FOLLOWERS("https://i.instagram.com/api/v1/friendships/%1\$s/followers/?max_id=%2\$s"),
        FOLLOWING("https://i.instagram.com/api/v1/friendships/%1\$s/following/?max_id=%2\$s")// count=12&
        /*FRIENDSHIPS("https://www.instagram.com/api/v1/friendships/show_many/"),*/,
        FOLLOW("https://www.instagram.com/web/friendships/%s/follow/"),
        UNFOLLOW("https://www.instagram.com/web/friendships/%s/unfollow/"),
        /*RESTRICT("https://www.instagram.com/api/v1/web/restrict_action/restrict/"),
        UNRESTRICT("https://www.instagram.com/api/v1/web/restrict_action/unrestrict/"),
        // method = POST, body = "target_user_id=<USER_ID>", expect "{"status":"ok"}" */
        /*BLOCK("https://www.instagram.com/api/v1/web/friendships/%d/block/"),
        UNBLOCK("https://www.instagram.com/api/v1/web/friendships/%d/unblock/"),
        // method = POST, expect "{"status":"ok"}" */

        // Saving
        SAVED("https://www.instagram.com/api/v1/feed/saved/posts/"),
        /*SAVE("https://www.instagram.com/web/save/%s/save/"),*/
        UNSAVE("https://www.instagram.com/web/save/%s/unsave/"),

        // Messaging
        INBOX("https://www.instagram.com/api/v1/direct_v2/inbox/?cursor=%s"),
        DIRECT("https://www.instagram.com/api/v1/direct_v2/threads/%1\$s/?cursor=%2\$s&limit=%3\$d")
        /* persistentBadging=true&folder=[0(PRIMARY)|1(GENERAL)]
        // Avoiding "limit" argument will default to 20, but can be more than that. */,
        SEEN("https://www.instagram.com/api/v1/direct_v2/threads/%1\$s/items/%2\$s/seen/"),

        // Logging in/out
        SIGN_OUT("https://www.instagram.com/accounts/logout/ajax/"),// MEDIA_ITEM

        RAW_QUERY("https://www.instagram.com/graphql/query"),
    }

    companion object {
        const val HANDLE_ERROR = 100
        const val postHash = "8c2a529969ee035a5063f2fc8602a0fd"
        const val savedHash = "2ce1d673055b99250e93b6f88f878fde"
        const val DEFAULT_TIMEOUT = 15000

        fun graphQlBody(cnfWrapper: PageConfig, shortcode: String): String {
            val siteData = cnfWrapper.define["SiteData"]!![1] as Map<String, Any>
            return "access_token=" +
                    "&__d=" + siteData["haste_site"] +
                    "&__user=0" +
                    "&__a=1" +
                    "&__dyn=" /*TODO*/ +
                    "&__csr=" /*TODO*/ +
                    "&__req=" /*TODO d or 3?*/ +
                    "&__hs=" + siteData["haste_session"] +
                    "&dpr=1" +
                    "&__ccg=" + (cnfWrapper.define["WebConnectionClassServerGuess"]!![1]
                    as Map<String, String>)["connectionClass"]!! +
                    "&__rev=" + (siteData["client_revision"] as Double)
                .toInt().toString() +
                    "&__s=" /*TODO*/ +
                    "&__hsi=" + siteData["haste_session"] +
                    "&__comet_req=7" +
                    "&fb_dtsg=" + (cnfWrapper.define["DTSGInitialData"]!![1]
                    as Map<String, String>)["token"]!! + // or DTSGInitData and async_get_token
                    "&jazoest=" /*TODO 26314 or 26301*/ +
                    "&lsd=" + (cnfWrapper.define["LSD"]!![1] as Map<String, String>)["token"]!! +
                    "&__spin_r=" + (siteData["__spin_r"] as Double).toInt() +
                    "&__spin_b=" + siteData["__spin_b"] +
                    "&__spin_t=" + (siteData["__spin_t"] as Double).toInt() +
                    "&fb_api_caller_class=RelayModern" +
                    "&fb_api_req_friendly_name=" /*TODO usePolarisSaveMediaSaveMutation or PolarisPostRootQuery*/ +
                    "&variables=" /*TODO shortcode or media id?!?*/ +
                    "&server_timestamps=true" +
                    "&doc_id=" /*TODO*/
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
            //var needAuth = false
            if (statusCode == 400) try {
                val failure = Gson()
                    .fromJson(String(data), Rest.ApiFailure::class.java)
                if (failure.lock) {
                    c.needAuthentication()
                    // needAuth = true
                }
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

        var RequestQueue.adder: Request<*>?
            get() = null
            set(req) {
                add(req)
            }
    }

    @Suppress("SpellCheckingInspection")
    class Headers(acc: Account, isImperative: Boolean = false, dm: DisplayMetrics? = null) :
        HashMap<String, String>() {
        init {
            this["accept-language"] = "en-GB"
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
                //if (acc.roll != null) this["x-instagram-ajax"] = acc.roll!! // TODO REMOVED?!?
                /*"x-fb-friendly-name": "usePolarisSaveMediaSaveMutation",
    "x-fb-lsd": "4XmgR5VLJlf9HL7hUQOtwn",*/// TODO NECESSARY?!?
            } else { // Cookie "rur" is different between MEDIA_ITEM and GET but the same between themselves
                this["accept"] = "*/*"
            }
            if (cookies.contains("csrftoken=")) this["x-csrftoken"] =
                cookies.substringAfter("csrftoken=").substringBefore(";")
            /* For this, load "https://www.instagram.com/static/bundles/es6/ConsumerLibCommons.js/5bb0ab377d4d.js"
             * Substring after "e.ASBD_ID='", substring before "'" */
            this["x-asbd-id"] = "198387" // STATIC
            // this["x-ig-www-claim"] = "hmac.AR1HhBJvtNorxBvZdmf8jZXs1JfsT2WhmwcKgtdyoYXsHCws" // TODO ?!?
            this["x-ig-app-id"] = "936619743392459" // STATIC
            this["cookie"] = cookies
            this["Referer"] = "https://www.instagram.com/"
            this["Referrer-Policy"] = "strict-origin-when-cross-origin"

            // Added myself
            this["Access-Control-Allow-Origin"] = "https://www.instagram.com/"
            this["Access-Control-Allow-Credentials"] = "true"
        }
    }
    /*fetch("https://www.instagram.com/graphql/query", {
  "headers": {
    "accept-language": "en-GB,en;q=0.9,fa-IR;q=0.8,fa;q=0.7,en-US;q=0.6",
    "content-type": "application/x-www-form-urlencoded",
    "sec-ch-prefers-color-scheme": "light",
    "sec-ch-ua": "\"Google Chrome\";v=\"111\", \"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"111\"",
    "sec-ch-ua-mobile": "?0",
    "sec-ch-ua-platform": "\"Windows\"",
    "sec-fetch-dest": "empty",
    "sec-fetch-mode": "cors",
    "sec-fetch-site": "same-origin",
    "viewport-width": "1366",
    "x-asbd-id": "198387",
    "x-csrftoken": "Md9BSLhvPT2DUhLoDiuK1n0RXtBAUXW9",
    "x-fb-friendly-name": "usePolarisSaveMediaSaveMutation",
    "x-fb-lsd": "4XmgR5VLJlf9HL7hUQOtwn",
    "x-ig-app-id": "936619743392459",
    "cookie": "mid=ZBv5VgALAAGDo48OHU8iPtxG8RBQ; ig_nrcb=1; ig_did=8DED6ECE-ADBC-4694-BA6B-63EDBE573C86; csrftoken=Md9BSLhvPT2DUhLoDiuK1n0RXtBAUXW9; ds_user_id=52110444768; sessionid=52110444768%3ADzqH57S3s7GMP6%3A27%3AAYcdr3uAS-Pg8u8gJ9LpCDqS_t2bPsJotJjqRRksTw; datr=__kbZId7dfu-p7kw8Tb1QxWU; shbid=\"7818\\05452110444768\\0541711091089:01f70b74d318245f50ebb7540aa769cf1fcd2feb7965ab9a5745780d18fb6175103d688a\"; shbts=\"1679555089\\05452110444768\\0541711091089:01f72006b0d381042142331ca5895878d107adad21a0b5e7a4f53dca25bd90890a21bd3f\"; rur=\"NCG\\05452110444768\\0541711091355:01f71a427f6269bdf5fdf61b90ccd7b80da87485586550697d5b64b2199b03b9edadd078\"",
    "Referer": "https://www.instagram.com/p/CpwCoY3IYzI/",
    "Referrer-Policy": "strict-origin-when-cross-origin"
  },
  "body": "access_token=
  &__d=www
  &__user=0
  &__a=1
  &__dyn=7xeUmwlE7ibwKBWo2vwAxu13w8CewSwMwNw9G2S0lW4o0B-q1ew65xO0FE2awt81s8hwGwQw9m1YwBgao6C0Mo5W3S7U2cxe0EUjwGzE2swwwNwKwHw8Xxm16wa-7-0iK2S3qazo7u1xwIwbS1bwzwTwKG0L85C1Iw
  &__csr=gbA8iNf96iAvTmhl8CmnF-Q5F9F_UzoOnGA9-ha4ei64EBbBxaqaCBxyq00huci4U08s40A83ayy09y1ya1Tw4owGA4wdJ0wwLw8-62a7k9a8xzB815Ddcywxo5G210vE81EC0hq04o8K8xW9w_w8B0ywiE5a01uqw1zC
  &__req=d
  &__hs=19439.HYP%3Ainstagram_web_pkg.2.1..0.1
  &dpr=1
  &__ccg=EXCELLENT
  &__rev=1007164808
  &__s=%3A9yh1xo%3Adgjmu0
  &__hsi=7213635163592090066
  &__comet_req=7
  &fb_dtsg=NAcMjWMKaLVoP3nT7AqztOTSWhxh98WUK8xwLPN9XkXi5qmE4QZTnhQ%3A17843683126168011%3A1679555068
  &jazoest=26301
  &lsd=4XmgR5VLJlf9HL7hUQOtwn
  &__spin_r=1007164808
  &__spin_b=trunk
  &__spin_t=1679555318
  &fb_api_caller_class=RelayModern
  &fb_api_req_friendly_name=usePolarisSaveMediaSaveMutation
  &variables=%7B%22media_id%22%3A%223057955718551407816%22%7D
  &server_timestamps=true
  &doc_id=18271948444105212",
  "method": "POST"
});*/

    //GET EXAMPLE
    /*fetch("https://www.instagram.com/api/v1/feed/reels_tray/", {
  "headers": {
    "accept": "* / *",
    "accept-language": "en-GB,en;q=0.9,fa-IR;q=0.8,fa;q=0.7,en-US;q=0.6",
    "sec-ch-prefers-color-scheme": "light",
    "sec-ch-ua": "\"Google Chrome\";v=\"111\", \"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"111\"",
    "sec-ch-ua-mobile": "?0",
    "sec-ch-ua-platform": "\"Windows\"",
    "sec-fetch-dest": "empty",
    "sec-fetch-mode": "cors",
    "sec-fetch-site": "same-origin",
    "viewport-width": "1366",
    "x-asbd-id": "198387",
    "x-csrftoken": "Md9BSLhvPT2DUhLoDiuK1n0RXtBAUXW9",
    "x-ig-app-id": "936619743392459",
    "x-ig-www-claim": "hmac.AR3Yzja13RaHk1xYtslwCaBIoHJmBIwl1Jh0HrCaWQN6Pz3z",
    "x-requested-with": "XMLHttpRequest",
    "cookie": "mid=ZBv5VgALAAGDo48OHU8iPtxG8RBQ; ig_nrcb=1; ig_did=8DED6ECE-ADBC-4694-BA6B-63EDBE573C86; csrftoken=Md9BSLhvPT2DUhLoDiuK1n0RXtBAUXW9; ds_user_id=52110444768; sessionid=52110444768%3ADzqH57S3s7GMP6%3A27%3AAYcdr3uAS-Pg8u8gJ9LpCDqS_t2bPsJotJjqRRksTw; datr=__kbZId7dfu-p7kw8Tb1QxWU; shbid=\"7818\\05452110444768\\0541711091089:01f70b74d318245f50ebb7540aa769cf1fcd2feb7965ab9a5745780d18fb6175103d688a\"; shbts=\"1679555089\\05452110444768\\0541711091089:01f72006b0d381042142331ca5895878d107adad21a0b5e7a4f53dca25bd90890a21bd3f\"; rur=\"NCG\\05452110444768\\0541711091338:01f764779199d73447c17a722a8b4030cb2498a4e1ab0e57878242822d318a90c414d93d\"",
    "Referer": "https://www.instagram.com/p/CpwCoY3IYzI/",
    "Referrer-Policy": "strict-origin-when-cross-origin"
},
"body": null,
"method": "GET"
});*/
}
