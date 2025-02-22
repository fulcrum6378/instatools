package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.Context.api
import ir.mahdiparastesh.instatools.Context.downloader
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.util.Lister
import ir.mahdiparastesh.instatools.util.Option
import ir.mahdiparastesh.instatools.util.Profile
import ir.mahdiparastesh.instatools.util.SimpleActions

class Stories(override val p: Profile) : Lister<Media>(), Profile.Section {
    override val numberOfClauses: Int = 1

    override fun fetch() {
        super.fetch()
        p.requireUserId()
        val reels = api.call<GraphQl>(
            Api.Endpoint.QUERY.url, GraphQl::class, true, GraphQlQuery.STORY.body(p.userId!!)
        ).data!!.xdt_api__v1__feed__reels_media!!.reels_media

        val media = reels.firstOrNull()?.items
        if (media.isNullOrEmpty())
            println("This user has no stories.")
        else media.forEachIndexed { i, r ->
            println("${i + 1}. " + r.link(p.userName))
            add(r)
        }
    }

    override fun fetch(reset: Boolean) {
        if (list?.isNotEmpty() == true) list?.clear()
        fetch()
    }

    override fun download(
        a: Array<String>, offsetOfClauses: Int, opt: HashMap<String, String?>?
    ) {
        this[a[offsetOfClauses]].forEach { med ->
            downloader.download(med, Option.quality(opt?.get(Option.QUALITY.key)), owner = p.userName)
            if (opt?.contains(Option.LIKE.key) == true)
                SimpleActions.actionMedia(med, GraphQlQuery.LIKE_STORY)
        }
    }
}
