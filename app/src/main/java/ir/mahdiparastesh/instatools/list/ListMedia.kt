package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.view.Expandable

abstract class ListMedia<C, F>(c: C, f: F) : ListPost<C, F>(c, f)
        where C : BaseActivity, F : BasePage<C> {

    abstract val media: ArrayList<Media>?

    override fun flexible(i: Int): FlexiblePost? {
        val med = media?.getOrNull(i) ?: return null
        return object : FlexiblePost(med.id, med.thumb()) {
            override fun typeDrw() = when {
                med.carousel_media != null -> typeStack
                med.video_versions != null -> typeVideo
                else -> null
            }
        }
    }

    override fun getItemCount() = media?.size ?: 0

    override fun Expandable.settings(pos: Int) {
        media = this@ListMedia.media?.getOrNull(pos)
    }
}
