package ir.mahdiparastesh.instatools

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQlQuery

fun main() {
    val api = Api()
    api.loadCookies()

    api.call<GraphQl>(
        Api.Endpoint.QUERY.url, GraphQl::class, true,
        GraphQlQuery.LIKE_POST.body("3567641127255644417")
    )
    println("Liked!")
}
