package ir.mahdiparastesh.instatools

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

fun main() {
    Api.loadCookiesFromFile()
    if (InetAddress.getLocalHost().hostName in arrayOf("CHIMAERA", "ANGELDUST"))
        Api.proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 8580))


    val gql = Api.json<GraphQl>(Api.Endpoint.QUERY.url, true, GraphQlQuery.FEED_TRAY.body())
    for (story in gql.data!!.xdt_api__v1__feed__reels_tray!!.tray)
        println("@${story.user.username}")  // ${story.ranked_position}
}
