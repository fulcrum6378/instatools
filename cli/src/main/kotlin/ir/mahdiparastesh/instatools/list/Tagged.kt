package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.Context.api
import ir.mahdiparastesh.instatools.Context.downloader
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.util.Lister.LazyLister
import ir.mahdiparastesh.instatools.util.Option
import ir.mahdiparastesh.instatools.util.Profile
import ir.mahdiparastesh.instatools.util.SimpleActions

class Tagged(override val p: Profile) : LazyLister<Media>(), Profile.Section {
    override val numberOfClauses: Int = 1

    override fun fetch() {
        super.fetch()
        p.requireUserId()
        val page = api.call<GraphQl>(
            Api.Endpoint.QUERY.url, GraphQl::class, true,
            if (cursor == null)
                GraphQlQuery.PROFILE_TAGGED.body(p.userId!!, "36")
            else
                GraphQlQuery.PROFILE_TAGGED_CURSORED.body(p.userId!!, "36", cursor!!)
        ).data!!.xdt_api__v1__usertags__user_id__feed_connection!!

        var caption: String
        for (e in page.edges) {
            caption = e.node.caption?.text?.replace("\n", " ")?.let { ": $it" } ?: ""
            println("$index. ${e.node.link()} - @${e.node.owner().username}$caption")
            add(e.node)
        }
        if (page.page_info.has_next_page) {
            cursor = page.edges.last().node.pk
            println("Enter `t ${p.userName}` again or just `t` to load more tagged posts from their profile...")
        } else endOfList()
    }

    override fun fetch(reset: Boolean) {
        fetchSome(reset)
    }

    override fun download(
        a: Array<String>, offsetOfClauses: Int, opt: HashMap<String, String?>?
    ) {
        this[a[offsetOfClauses]].forEach { med ->
            downloader.download(med, Option.quality(opt?.get(Option.QUALITY.key)))
            if (opt?.contains(Option.LIKE.key) == true)
                SimpleActions.actionMedia(med, GraphQlQuery.LIKE_POST)
        }
    }
}
