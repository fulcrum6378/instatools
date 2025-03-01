package ir.mahdiparastesh.instatools.api

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

class HtmlDataTest {
    val mainPageFile = File("Downloads\\instagram.html")
    val configFile = File("Downloads\\config.json")

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() {
            Api.loadCookiesFromFile()
            if (InetAddress.getLocalHost().hostName in arrayOf("CHIMAERA", "ANGELDUST"))
                Api.proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 8580))
            Api.html("https://www.instagram.com/")
        }
    }

    @Test

}
