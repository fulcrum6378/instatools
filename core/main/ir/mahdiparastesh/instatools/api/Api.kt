package ir.mahdiparastesh.instatools.api

import ir.mahdiparastesh.instatools.api.Api.Endpoint.QUERY
import ir.mahdiparastesh.instatools.api.Api.cookies
import ir.mahdiparastesh.instatools.api.Api.html
import ir.mahdiparastesh.instatools.api.Api.json
import ir.mahdiparastesh.instatools.util.Utils
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/**
 * A static class that handles all calls to the new and old Instagram APIs efficiently via [json].
 * Also opens Instagram web pages via [html].
 * [cookies] are required for this class to work.
 */
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

    /** Makes a call to the new and old Instagram APIs. */
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
        } catch (e: IOException) {
            throw FailureException(e)
        }

        val text = if (responseCode == 200) try {
            con.inputStream.bufferedReader().readText()
        } catch (e: IOException) {
            throw FailureException(ERR_BROKEN_CON, e)
        } else
            throw FailureException(responseCode)

        if (System.getenv("debug") == "1")
            println(text)
        /*FileOutputStream(File("Downloads/1.json"))
            .use { it.write(text.encodeToByteArray()) }*/

        if (text.startsWith("<!DOCTYPE html>"))
            throw FailureException(ERR_LOGGED_OUT)

        val data = json.decodeFromString<JSON>(text)
        if (data is GraphQl && data.data == null)
            throw FailureException(ERR_GRAPHQL_FAILED)
        return data
    }

    fun graphQl(body: String): GraphQl =
        json<GraphQl>(QUERY.url, true, body)

    /** Opens an Instagram webpage and reads its contents. */
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
        } catch (e: IOException) {
            throw FailureException(e)
        }

        if (responseCode == 200) try {
            return con.inputStream.bufferedReader().readText()
        } catch (e: IOException) {
            throw FailureException(ERR_BROKEN_CON, e)
        } else
            throw FailureException(responseCode)
    }


    /**
     * [QUERY] exclusively belongs to the new Instagram API calls.
     * Other endpoints contact the old API.
     * Some of the old API endpoints stopped working or raised Instagram's suspicion.
     * It is recommended to migrate to calling the new API where Instagram isn't that suspicious.
     * Although the new API can make Instagram suspicious as well.
     */
    enum class Endpoint(val url: String) {
        QUERY("https://www.instagram.com/graphql/query"),

        // information
        USER_INFO("https://www.instagram.com/api/v1/users/%s/info/"),
        PROFILE_INFO("https://www.instagram.com/api/v1/users/web_profile_info/?username=%s"),
        SAVED("https://www.instagram.com/api/v1/feed/saved/posts/"),
        //MEDIA_INFO("https://www.instagram.com/api/v1/media/%s/info/"),

        // logging in/out
        LOGOUT("https://www.instagram.com/accounts/logout/ajax/"),
    }


    /** Gets thrown when any error occurs while contacting the Instagram APIs. */
    class FailureException(
        val code: Int, e: IOException? = null
    ) : IllegalStateException(
        "API ERROR: " + when (code) {
            ERR_NO_INTERNET -> "No internet connection!"
            ERR_CON_PROXY -> "Couldn't connect to the proxy server!"
            ERR_CON -> "Couldn't connect to Instagram!"
            ERR_BROKEN_CON ->
                "Connection was broken!" + (if (e != null) describeException(e) else "")
            ERR_LOGGED_OUT, 401 -> "You've been logged out!" +
                (if (code == 401) " (HTTP error code $code)" else "")
            ERR_GRAPHQL_FAILED -> "Operation failed; presumably you've been logged out!"
            ERR_NO_COOKIES -> "No cookies are set!"
            ERR_CON_UNKNOWN_ERR -> "Unknown connection error: ${describeException(e!!)}"
            404 -> "Not found!"
            429 -> "Too many requests!"
            else -> "HTTP error code $code!"
        }
    ), Utils.InstaToolsException {

        constructor(e: IOException) : this(
            when (e) {
                is java.net.UnknownHostException -> ERR_NO_INTERNET
                is java.net.ConnectException -> ERR_CON_PROXY
                is java.net.SocketTimeoutException -> ERR_CON
                is javax.net.ssl.SSLHandshakeException, is java.net.SocketException -> ERR_BROKEN_CON
                is java.net.ProtocolException -> ERR_LOGGED_OUT
                else -> ERR_CON_UNKNOWN_ERR
            }, e
        )
    }

    const val ERR_NO_INTERNET = -1
    const val ERR_CON_PROXY = -2
    const val ERR_CON = -3
    const val ERR_BROKEN_CON = -4
    const val ERR_LOGGED_OUT = -5
    const val ERR_GRAPHQL_FAILED = -6
    const val ERR_NO_COOKIES = -10
    const val ERR_CON_UNKNOWN_ERR = -11

    private fun describeException(e: Exception): String =
        e.javaClass.`package`.name + (if (e.message != null) ": ${e.message}" else "")
}
