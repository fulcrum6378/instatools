package ir.mahdiparastesh.instatools.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ir.mahdiparastesh.instatools.util.Utils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.*
import javax.net.ssl.HttpsURLConnection
import kotlin.reflect.KClass

class Api {
    private var cookies = ""
    var proxy: Proxy = Proxy.NO_PROXY
    val connectTimeout = 5000

    init {
        if (InetAddress.getLocalHost().hostName in arrayOf("CHIMAERA", "ANGELDUST"))
            proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 8580))
    }

    fun loadCookies(path: String = "cookies.txt"): Boolean {
        val f = File(path)
        if (!f.exists()) return false
        cookies = FileInputStream(f).use { String(it.readBytes()) }
        return true
    }

    fun setProxy(newProxy: String? = null) {
        proxy =
            if (newProxy == null) Proxy.NO_PROXY
            else {
                val uri = URI(newProxy)
                Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port))
            }
    }

    fun <JSON> call(
        url: String,
        clazz: KClass<*>,
        isPost: Boolean = false,
        body: String? = null,
        generics: Array<KClass<*>>? = null
    ): JSON {
        val con = URI(url).toURL().openConnection(proxy) as HttpsURLConnection
        con.requestMethod = if (isPost) "POST" else "GET"
        con.setRequestProperty("x-asbd-id", "129477")
        if (cookies.contains("csrftoken=")) con.setRequestProperty(
            "x-csrftoken",
            cookies.substringAfter("csrftoken=").substringBefore(";")
        )
        con.setRequestProperty("x-ig-app-id", "936619743392459")
        con.setRequestProperty("cookie", cookies)

        con.useCaches = false
        con.connectTimeout = connectTimeout
        con.doInput = true
        con.readTimeout = 10000
        if (isPost && body != null) {
            con.doOutput = true
            con.setRequestProperty("content-type", "application/x-www-form-urlencoded")
            if (System.getenv("debug") == "1")
                println("Post Body: $body")
        }

        val responseCode = try {
            if (isPost && body != null)
                con.outputStream.bufferedWriter().use { it.write(body) }
            con.responseCode
        } catch (_: UnknownHostException) {
            throw FailureException(-1)
        } catch (_: ConnectException) {
            throw FailureException(if (proxy != Proxy.NO_PROXY) -10 else -2)
        } catch (_: SocketTimeoutException) {
            throw FailureException(-2)
        } catch (_: ProtocolException) { // more than 20 redirections!
            throw FailureException(-4)
        }

        val text = if (responseCode == 200) try {
            con.inputStream.bufferedReader().readText()
        } catch (_: IOException) {
            throw FailureException(-3)
        } else
            throw FailureException(responseCode)

        if (System.getenv("debug") == "1") {
            println(text)
            //FileOutputStream(File("Downloads/1.json")).use { it.write(text.encodeToByteArray()) }
        }
        if (text.startsWith("<!DOCTYPE html>"))
            throw FailureException(-4)

        val json = Gson().fromJson(
            text,
            if (generics != null) TypeToken.getParameterized(
                clazz.java, *generics.map { it.java }.toTypedArray()
            ).type else clazz.java
        ) as JSON
        if (clazz == GraphQl::class && (json as GraphQl).data == null)
            throw FailureException(-5)
        return json
    }

    fun page(url: String): String {
        val con = URI(url).toURL().openConnection(proxy) as HttpsURLConnection
        con.requestMethod = "GET"
        con.setRequestProperty("accept", "text/html")
        con.setRequestProperty(
            "user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/133.0.0.0 Safari/537.36"
        )
        con.setRequestProperty("cookie", cookies)

        con.useCaches = false
        con.connectTimeout = connectTimeout
        con.doInput = true
        con.readTimeout = 12000

        val responseCode = try {
            con.responseCode
        } catch (_: UnknownHostException) {
            throw FailureException(-1)
        } catch (_: ConnectException) {
            throw FailureException(if (proxy != Proxy.NO_PROXY) -10 else -2)
        } catch (_: SocketTimeoutException) {
            throw FailureException(-1)
        } catch (_: ProtocolException) {
            throw FailureException(-4)
        }

        if (responseCode == 200) try {
            return con.inputStream.bufferedReader().readText()
        } catch (_: IOException) {
            throw FailureException(-3)
        } else
            throw FailureException(responseCode)
    }

    @Suppress("unused")
    enum class Endpoint(val url: String) {
        QUERY("https://www.instagram.com/graphql/query"),

        // information
        USER_INFO("https://www.instagram.com/api/v1/users/%s/info/"),
        PROFILE_INFO("https://www.instagram.com/api/v1/users/web_profile_info/?username=%s"),
        MEDIA_INFO("https://www.instagram.com/api/v1/media/%s/info/"),
        SAVED("https://www.instagram.com/api/v1/feed/saved/posts/"),

        // direct messages
        INBOX("https://www.instagram.com/api/v1/direct_v2/inbox/?cursor=%s"),
        DIRECT("https://www.instagram.com/api/v1/direct_v2/threads/%1\$s/?cursor=%2\$s&limit=%3\$d"),
        SEEN("https://www.instagram.com/api/v1/direct_v2/threads/%1\$s/items/%2\$s/seen/"),

        // friendships
        FOLLOWERS("https://www.instagram.com/api/v1/friendships/%1\$s/followers/?count=200&max_id=%2\$s"),
        FOLLOWING("https://www.instagram.com/api/v1/friendships/%1\$s/following/?count=200&max_id=%2\$s"),
        FRIENDSHIPS_MANY("https://www.instagram.com/api/v1/friendships/show_many/"),
        FRIENDSHIP("https://www.instagram.com/api/v1/friendships/show/%s/"), // GET

        // logging in/out
        LOGOUT("https://www.instagram.com/accounts/logout/ajax/")
    }

    inner class FailureException(status: Int) : IllegalStateException(
        "API ERROR: " + when (status) {
            -1 -> "No internet connection!"
            -2 -> "Couldn't connect to Instagram!"
            -3 -> "Connection was broken!"
            -4, 401 -> "You've been logged out!" + (if (status == 401) " (HTTP error code $status)" else "")
            -5 -> "Operation failed; presumably you've been logged out!"
            -10 -> "Couldn't connect to the proxy server!"
            404 -> "Not found!"
            429 -> "Too many requests!"
            else -> "HTTP error code $status!"
        }
    ), Utils.InstaToolsException
}
