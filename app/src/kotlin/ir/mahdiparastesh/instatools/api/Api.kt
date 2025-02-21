package ir.mahdiparastesh.instatools.api

import android.net.Uri
import android.text.TextUtils
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import ir.mahdiparastesh.instatools.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.URI
import java.util.regex.Pattern
import javax.net.ssl.HttpsURLConnection
import kotlin.reflect.KClass

/** Controls all API interactions with Instagram Web API using Volley and Gson. */
object Api {
    const val DEFAULT_CONNECT_TIMEOUT = 5000
    var cookies = ""

    /**
     * @return JSON on success, null if the procedure fails
     */
    @WorkerThread
    suspend fun <JSON> call(
        url: String,
        clazz: KClass<*>,
        generics: Array<KClass<*>>? = null,
        isPost: Boolean = false,
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
        con.connectTimeout = DEFAULT_CONNECT_TIMEOUT
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

        val responseCode = try {
            con.responseCode
        } catch (_: ProtocolException) {
            if (onError != null) withContext(Dispatchers.Main) { onError(-4) }
            return null
        }

        val text = if (responseCode == 200) try {
            con.inputStream.bufferedReader().readText()
        } catch (_: IOException) {
            if (onError != null) withContext(Dispatchers.Main) { onError(-2) }
            return null
        } else {
            if (onError != null) withContext(Dispatchers.Main) { onError(responseCode) }
            null
        }

        return try {
            Gson().fromJson(
                text,
                if (generics != null) TypeToken.getParameterized(
                    clazz.java, *generics.map { it.java }.toTypedArray()
                ).type else clazz.java
            ) as JSON
        } catch (_: JsonSyntaxException) {
            if (onError != null) withContext(Dispatchers.Main) { onError(-3) }
            null
        }
    }

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
        con.connectTimeout = DEFAULT_CONNECT_TIMEOUT
        con.doInput = true
        con.readTimeout = 12000
        try {
            con.connect()
        } catch (_: SocketTimeoutException) {
            if (onError != null) withContext(Dispatchers.Main) { onError(-1) }
            return null
        }

        val responseCode = try {
            con.responseCode
        } catch (_: ProtocolException) {
            if (onError != null) withContext(Dispatchers.Main) { onError(-4) }
            return null
        }

        return if (responseCode == 200) try {
            con.inputStream.bufferedReader().readText()
        } catch (_: IOException) {
            if (onError != null) withContext(Dispatchers.Main) { onError(-2) }
            null
        } else {
            if (onError != null) withContext(Dispatchers.Main) { onError(responseCode) }
            null
        }
    }

    @StringRes
    fun error(code: Int): Int = when (code) {
        -1 -> R.string.connectionFailure
        -2 -> R.string.connectionBroken
        -3 -> R.string.invalidResponse
        -4 -> R.string.loggedOut
        401 -> R.string.loggedOut401
        404 -> R.string.notFound
        429 -> R.string.manyRequests
        else -> R.string.httpError
    }

    enum class Endpoint(val url: String) {
        QUERY("https://www.instagram.com/graphql/query"),

        // information
        USER_INFO("https://www.instagram.com/api/v1/users/%s/info/"),
        PROFILE_INFO("https://www.instagram.com/api/v1/users/web_profile_info/?username=%s"),
        SAVED("https://www.instagram.com/api/v1/feed/saved/posts/"),
        SEARCH(
            "https://www.instagram.com/api/v1/web/search/topsearch/?context=blended&query=%s" +
                "&include_reel=false&search_surface=web_top_search"
        ),

        // direct messages
        INBOX("https://www.instagram.com/api/v1/direct_v2/inbox/?cursor=%s"),
        DIRECT("https://www.instagram.com/api/v1/direct_v2/threads/%1\$s/?cursor=%2\$s&limit=%3\$d"),
        SEEN("https://www.instagram.com/api/v1/direct_v2/threads/%1\$s/items/%2\$s/seen/"),

        // friendships
        FOLLOWERS("https://www.instagram.com/api/v1/friendships/%1\$s/followers/?count=200&max_id=%2\$s"),
        FOLLOWING("https://www.instagram.com/api/v1/friendships/%1\$s/following/?count=200&max_id=%2\$s"),
        FRIENDSHIPS_MANY("https://www.instagram.com/api/v1/friendships/show_many/"),
        //FRIENDSHIP("https://www.instagram.com/api/v1/friendships/show/%s/"),

        // logging in/out
        LOGOUT("https://www.instagram.com/accounts/logout/ajax/"),
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
}
