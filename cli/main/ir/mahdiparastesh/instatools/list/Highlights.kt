package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.Context.api
import ir.mahdiparastesh.instatools.Context.downloader
import ir.mahdiparastesh.instatools.api.*
import ir.mahdiparastesh.instatools.util.Lister
import ir.mahdiparastesh.instatools.util.Option
import ir.mahdiparastesh.instatools.util.Profile
import ir.mahdiparastesh.instatools.util.SimpleActions

class Highlights(override val p: Profile) : Lister<Media>(), Profile.Section {
    private val trays: HashMap<String, Story> = hashMapOf()
    private var currentTray: String? = null
    override var list: ArrayList<Media>?
        get() = currentTray?.let { trays[it]?.items }
        set(_) {}
    override val numberOfClauses: Int = 2

    override fun fetch() {
        super.fetch()
        p.requireUserId()
        val hls = api.call<GraphQl>(
            Api.Endpoint.QUERY.url, GraphQl::class, true,
            GraphQlQuery.PROFILE_HIGHLIGHTS_TRAY.body(p.userId!!)
        ).data!!.highlights!!.edges

        if (hls.isEmpty()) println("This user has no highlighted stories.")
        else hls.forEachIndexed { _, tray ->
            val hlId = tray.node.highlightId()
            println(
                "$hlId:" +
                        (if (tray.node.title != null) " ${tray.node.title} -" else "") +
                        " ${tray.node.link()}" +
                        (if (tray.node.items != null) " (${tray.node.items!!.size} items)" else "")
            )
            if (hlId in trays && trays[hlId]!!.items == null)
                tray.node.items = trays[hlId]!!.items
            trays[hlId] = tray.node
        }
    }

    override fun fetch(reset: Boolean) {
        fetch()
    }

    override fun download(
        a: Array<String>, offsetOfClauses: Int, opt: HashMap<String, String?>?
    ) {
        currentTray =
            if (a[offsetOfClauses].startsWith("highlight:")) a[offsetOfClauses].substring(10)
            else a[offsetOfClauses]
        val ready = trays[currentTray]?.items
        if (ready == null) {
            val apiId = "\"highlight:${currentTray}\""
            val page = api.call<GraphQl>(
                Api.Endpoint.QUERY.url, GraphQl::class, true,
                GraphQlQuery.HIGHLIGHTS.body(apiId, apiId)
            ).data!!.xdt_api__v1__feed__reels_media__connection!!
            page.edges.forEach { tray ->
                tray.node.items = ArrayList(tray.node.items!!)
                trays[currentTray!!] = tray.node
            }
        }
        if (a.size == offsetOfClauses + 1)
            println("This tray contains only ${trays[currentTray!!]!!.items!!.size} items.")
        else this[a[offsetOfClauses + 1]].forEach { med ->
            downloader.download(med, Option.quality(opt?.get(Option.QUALITY.key)), owner = p.userName)
            if (opt?.contains(Option.LIKE.key) == true)
                SimpleActions.actionMedia(med, GraphQlQuery.LIKE_STORY)
        }
    }
}
