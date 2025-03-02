package ir.mahdiparastesh.instatools

import ir.mahdiparastesh.instatools.api.Api
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

fun main() {
    Api.loadCookiesFromFile()
    if (InetAddress.getLocalHost().hostName in arrayOf("CHIMAERA", "ANGELDUST"))
        Api.proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 8580))

    /*Api.json<GraphQl>(
        Api.Endpoint.QUERY.url, true, GraphQlQuery.LIKE_POST.body("3567641127255644417")
    )
    println("Liked!")*/
}
