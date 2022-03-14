package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.view.Expandable
import java.util.concurrent.CopyOnWriteArrayList

abstract class ListEdge<C, F>(c: C, f: F) : ListPost<C, F>(c, f)
        where C : BaseActivity, F : BasePage<C> {

    abstract val edges: CopyOnWriteArrayList<Profile.EdgePost>?

    override fun flexible(i: Int): FlexiblePost? {
        val node = edges?.getOrNull(i)?.node ?: return null
        return object : FlexiblePost(node.id, node.thumbnail_src) {
            override fun typeDrw() = when (node.__typename) {
                "GraphSidecar" -> typeStack
                "GraphVideo" -> typeVideo
                "GraphImage" -> null
                else -> null
            }

            override fun isStored(): Boolean {
                if (node.__typename == "GraphSidecar") return false
                val theirs = c.m.files.value?.filter { it.startsWith("${node.owner.username}_") }
                    ?.map { it.substringBeforeLast(".").substringAfterLast("_") }
                    ?: return false
                return id in theirs
            }
        }
    }

    override fun getItemCount() = edges?.size ?: 0

    override fun Expandable.settings(pos: Int) {
        node = edges?.getOrNull(pos)?.node
    }
}
