package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.view.Expandable

abstract class ListEdge<C, F>(c: C, f: F) : ListPost<C, F>(c, f)
        where C : BaseActivity, F : BasePage<C> {

    abstract val edges: ArrayList<Profile.EdgePost>?

    override fun flexible(i: Int): FlexiblePost? {
        val node = edges?.getOrNull(i)?.node ?: return null
        return object : FlexiblePost(node.id, node.thumbnail_src) {
            override fun typeDrw() = when (node.__typename) {
                "GraphSidecar" -> typeStack
                "GraphVideo" -> typeVideo
                "GraphImage" -> null
                else -> null
            }
        }
    }

    override fun getItemCount() = edges?.size ?: 0

    override fun Expandable.settings(pos: Int) {
        node = edges?.getOrNull(pos)?.node
    }
}
