package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.Context.downloadTask
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.job.SimpleTasks
import ir.mahdiparastesh.instatools.util.LazyLister
import ir.mahdiparastesh.instatools.util.Option
import ir.mahdiparastesh.instatools.util.Profile

class Reels(override val p: Profile) : LazyLister<Media>(), Profile.Section {
    override val numberOfClauses: Int = 1

    override fun fetch() {
        p.requireUserId()
        val page = Api.json<GraphQl>(
            Api.Endpoint.QUERY.url, true,
            if (cursor == null)
                GraphQlQuery.PROFILE_REELS_INITIAL.body(p.userId!!, "12")
            else
                GraphQlQuery.PROFILE_REELS_MORE.body(p.userId!!, "12", cursor!!)
        ).data!!.xdt_api__v1__clips__user__connection_v2!!

        for (e in page.edges) {
            println("$index. ${e.node.media.link()}")
            add(e.node.media)
        }
        if (page.page_info.has_next_page) {
            cursor = page.edges.last().node.media.id()
            println("Type `r ${p.userName}` again or just `r` to load more reels from their profile...")
            println("Type `r -h` to see the available options...")
        } else endOfList()
    }

    override fun fetch(reset: Boolean) {
        fetchSome(reset)
    }

    override fun download(
        a: Array<String>, offsetOfClauses: Int, opt: HashMap<String, String?>?
    ) {
        this[a[offsetOfClauses]].forEach { med ->
            // TODO this Media model is incomplete
            downloadTask.download(med, Option.quality(opt?.get(Option.QUALITY.key)))
            if (opt?.contains(Option.LIKE.key) == true)
                SimpleTasks.actionMedia(med, GraphQlQuery.LIKE_POST)
        }
    }
}
