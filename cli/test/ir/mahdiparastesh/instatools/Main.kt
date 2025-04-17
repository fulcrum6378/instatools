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


    /*val gql = Api.json<GraphQl>(Api.Endpoint.QUERY.url, true, GraphQlQuery.FEED_TRAY.body())
    for (story in gql.data!!.xdt_api__v1__feed__reels_tray!!.tray)
        println("@${story.user.username}")*/  // ${story.ranked_position}


    /*println(
        "PROFILE_POSTS_MORE: " + Api.json<GraphQl>(
            Api.Endpoint.QUERY.url, true,
            GraphQlQuery.PROFILE_POSTS_MORE.body("martinasebellin", "50", "null")
        ).data!!.xdt_api__v1__feed__user_timeline_graphql_connection!!.edges.size
    )*/

    /*println(
        "PROFILE_POSTS_INITIAL: " + Api.json<GraphQl>(
            Api.Endpoint.QUERY.url, true,
            GraphQlQuery.PROFILE_POSTS_INITIAL.body("martinasebellin", "50")
        ).data!!.xdt_api__v1__feed__user_timeline_graphql_connection!!.edges.size
    )*/  // `friendship_status` is not null here!


    /*println(
        "PROFILE_REELS_INITIAL: " + Api.json<GraphQl>(
            Api.Endpoint.QUERY.url, true,
            GraphQlQuery.PROFILE_REELS_INITIAL.body("865018431", "50")
        ).data!!.xdt_api__v1__clips__user__connection_v2!!.edges.size
    )*/

    @Suppress("SpellCheckingInspection")
    println(
        "PROFILE_REELS_MORE: " + Api.json<GraphQl>(
            Api.Endpoint.QUERY.url, true,
            GraphQlQuery.PROFILE_REELS_INITIAL.body(
                "865018431", "50",
                //"QVFCZmZkazQxLWswUWZWUTBESFoyR2k3aDBHSWNZaXhCUS1TQV9Kbml2bGk2R0FMQ29DTUJlTGs4dmRReENKVzEtYlIycnd6WURoS2p3X2tqT2c1VnRvTg=="  // 12
                "QVFCZzJRZWs0Mnd3YWhNb1U2bHdHbkdZOExDYl9lc1Itb2tQRE9GWW1uUXY2YkJRTFJVVXNuTWFlZTdxM19hcHlhYTdYa0p6RnpPTnNKZUd6dk9tR3lGUQ=="  // 12
            )
        ).data!!.xdt_api__v1__clips__user__connection_v2!!.edges.size
    )


    /*Api.json<GraphQl>(
        Api.Endpoint.QUERY.url, true,
        GraphQlQuery.PROFILE_TAGGED_MORE.body("865018431", "36", "null")
    )*/  // ERRORED even using the untouched API query!

    /*println(
        "PROFILE_TAGGED_INITIAL: " + Api.json<GraphQl>(
            Api.Endpoint.QUERY.url, true,
            GraphQlQuery.PROFILE_TAGGED_INITIAL.body("865018431", "50")
        ).data!!.xdt_api__v1__usertags__user_id__feed_connection!!.edges.size
    )*/

    /*println(
        "PROFILE_TAGGED_MORE: " + Api.json<GraphQl>(
            Api.Endpoint.QUERY.url, true,
            GraphQlQuery.PROFILE_TAGGED_MORE
                //.body("865018431", "36", "2952623461718966878")  // 21
                //.body("865018431", "50", "2535898934532150752")  // 21
                .body("865018431", "50", "2410409820234015809")  // 21
        ).data!!.xdt_api__v1__usertags__user_id__feed_connection!!.edges.size
    )*/


    // PolarisProfileTaggedTabContentQuery's original behaviour:
    // {"count":12,"user_id":"865018431"}
    // {"after":"2952623461718966878","before":null,"count":12,"first":12,"last":null,"user_id":"865018431"}
    // {"after":"2539993456794616722","before":null,"count":12,"first":12,"last":null,"user_id":"865018431"}
    // {"after":"2483537805919588373","before":null,"count":12,"first":12,"last":null,"user_id":"865018431"}
}
