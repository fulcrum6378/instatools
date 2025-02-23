package ir.mahdiparastesh.instatools

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest

fun main() {
    val api = Api()
    api.loadCookies()

    /*api.call<GraphQl>(
        Api.Endpoint.QUERY.url, GraphQl::class, true,
        GraphQlQuery.LIKE_POST.body("3567641127255644417")
    )
    println("Liked!")*/

    val info = api.call<Rest.UserInfo>(Api.Endpoint.USER_INFO.url.format("8337021434")).user
    println(info.picture())
}
