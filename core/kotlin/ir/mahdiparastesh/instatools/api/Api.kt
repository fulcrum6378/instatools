package ir.mahdiparastesh.instatools.api

import ir.mahdiparastesh.instatools.util.Utils
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException

object Api {
    var cookies = ""
    var proxy: Proxy = Proxy.NO_PROXY
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    var connectTimeout = 5000

    fun loadCookiesFromFile(path: String = "cookies.txt"): Boolean {
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

    @Throws(FailureException::class)
    inline fun <reified JSON> json(
        url: String,
        isPost: Boolean = false,
        body: String? = null,
    ): JSON {
        if (cookies.isBlank()) throw FailureException(ERR_NO_COOKIES)

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
            throw FailureException(ERR_NO_INTERNET)
        } catch (_: ConnectException) {
            throw FailureException(ERR_CON_PROXY)
        } catch (_: SocketTimeoutException) {
            throw FailureException(ERR_CON)
        } catch (_: SSLHandshakeException) {
            throw FailureException(ERR_BROKEN_CON)
        } catch (_: ProtocolException) { // more than 20 redirections!
            throw FailureException(ERR_LOGGED_OUT)
        }

        val text = if (responseCode == 200) try {
            con.inputStream.bufferedReader().readText()
        } catch (_: IOException) {
            throw FailureException(ERR_BROKEN_CON)
        } else
            throw FailureException(responseCode)

        if (System.getenv("debug") == "1") {
            println(text)
            //FileOutputStream(File("Downloads/1.json")).use { it.write(text.encodeToByteArray()) }
        }
        if (text.startsWith("<!DOCTYPE html>"))
            throw FailureException(ERR_LOGGED_OUT)

        val data = json.decodeFromString<JSON>(text)
        if (data is GraphQl && data.data == null)
            throw FailureException(ERR_GRAPHQL_FAILED)
        return data
    }

    @Throws(FailureException::class)
    fun html(url: String): String {
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
            throw FailureException(ERR_NO_INTERNET)
        } catch (_: ConnectException) {
            throw FailureException(ERR_CON_PROXY)
        } catch (_: SocketTimeoutException) {
            throw FailureException(ERR_CON)
        } catch (_: SSLHandshakeException) {
            throw FailureException(ERR_BROKEN_CON)
        } catch (_: ProtocolException) {
            throw FailureException(ERR_LOGGED_OUT)
        }

        if (responseCode == 200) try {
            return con.inputStream.bufferedReader().readText()
        } catch (_: IOException) {
            throw FailureException(ERR_BROKEN_CON)
        } else
            throw FailureException(responseCode)
    }

    enum class Endpoint(val url: String) {
        QUERY("https://www.instagram.com/graphql/query"),

        // information
        USER_INFO("https://www.instagram.com/api/v1/users/%s/info/"),
        PROFILE_INFO("https://www.instagram.com/api/v1/users/web_profile_info/?username=%s"),
        SAVED("https://www.instagram.com/api/v1/feed/saved/posts/"),
        //MEDIA_INFO("https://www.instagram.com/api/v1/media/%s/info/"),

        // friendships
        //FOLLOWERS("https://www.instagram.com/api/v1/friendships/%1\$s/followers/?count=200&max_id=%2\$s"),
        //FOLLOWING("https://www.instagram.com/api/v1/friendships/%1\$s/following/?count=200&max_id=%2\$s"),
        //FRIENDSHIPS_MANY("https://www.instagram.com/api/v1/friendships/show_many/"),
        //FRIENDSHIP("https://www.instagram.com/api/v1/friendships/show/%s/"),

        // logging in/out
        LOGOUT("https://www.instagram.com/accounts/logout/ajax/"),
    }

    class FailureException(val code: Int) : IllegalStateException(
        "API ERROR: " + when (code) {
            ERR_NO_INTERNET -> "No internet connection!"
            ERR_CON_PROXY -> "Couldn't connect to the proxy server!"
            ERR_CON -> "Couldn't connect to Instagram!"
            ERR_BROKEN_CON -> "Connection was broken!"
            ERR_LOGGED_OUT, 401 -> "You've been logged out!" +
                (if (code == 401) " (HTTP error code $code)" else "")
            ERR_GRAPHQL_FAILED -> "Operation failed; presumably you've been logged out!"
            ERR_NO_COOKIES -> "No cookies are set!"
            404 -> "Not found!"
            429 -> "Too many requests!"
            else -> "HTTP error code $code!"
        }
    ), Utils.InstaToolsException

    const val ERR_NO_INTERNET = -1
    const val ERR_CON_PROXY = -2
    const val ERR_CON = -3
    const val ERR_BROKEN_CON = -4
    const val ERR_LOGGED_OUT = -5
    const val ERR_GRAPHQL_FAILED = -6
    const val ERR_NO_COOKIES = -10
}
