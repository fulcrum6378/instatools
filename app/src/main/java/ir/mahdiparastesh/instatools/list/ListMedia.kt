package ir.mahdiparastesh.instatools.list

import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.view.Expandable
import java.util.concurrent.CopyOnWriteArrayList

abstract class ListMedia<C, F>(c: C, f: F) : ListPost<C, F>(c, f)
        where C : BaseActivity, F : BasePage<C> {

    abstract val media: CopyOnWriteArrayList<Media>?

    override fun flexible(i: Int): FlexiblePost? {
        val med = media?.getOrNull(i) ?: return null
        return object : FlexiblePost(med.pk ?: med.id, med.thumb()) {
            override fun typeDrw() = when {
                med.carousel_media != null -> typeStack
                med.video_versions != null -> typeVideo
                else -> null
            }

            override fun isStored(): Boolean {
                val theirs = c.m.files.value?.filter { it.startsWith("${med.user.username}_") }
                    ?.map { it.substringBeforeLast(".").substringAfterLast("_") }
                    ?: return false
                return if (med.carousel_media != null)
                    med.carousel_media!!.all { it.pk in theirs }
                else med.pk in theirs
            }
        }
    }

    override fun getItemCount() = media?.size ?: 0

    override fun Expandable.settings(pos: Int) {
        media = this@ListMedia.media?.getOrNull(pos)
    }
}
