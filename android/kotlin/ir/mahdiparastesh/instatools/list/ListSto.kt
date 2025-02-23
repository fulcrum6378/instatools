package ir.mahdiparastesh.instatools.list

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.data.Queued.Companion.queue
import ir.mahdiparastesh.instatools.databinding.ListStoBinding
import ir.mahdiparastesh.instatools.frag.PageSto
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListSto(private val c: Viewer, private val f: PageSto) :
    RecyclerView.Adapter<AnyViewHolder<ListStoBinding>>() {
    private val hlNumAdd: Int by lazy { if (c.mm.story != null) 0 else 1 }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListStoBinding> =
        AnyViewHolder(ListStoBinding.inflate(c.layoutInflater, parent, false))

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(h: AnyViewHolder<ListStoBinding>, i: Int) {
        var isHL = true
        val story = when {
            c.mm.story != null && i == 0 -> {
                isHL = false
                c.mm.story
            }
            c.mm.story != null && i != 0 ->
                c.mm.highlights?.edges?.getOrNull(i - 1)?.node
            else -> c.mm.highlights?.edges?.getOrNull(i)?.node
        } ?: return

        // details
        h.b.title.text =
            if (!isHL) c.getString(R.string.vwStoryReel)
            else "${i + hlNumAdd}. ${story.title}"
        if (!isHL) h.b.desc.text = c.getString(R.string.vwReelDesc, story.items!!.size)
        if (!isHL) h.b.icon.setImageResource(R.drawable.instagram)
        else story.cover_media?.cropped_image_version.apply {
            if (this != null) Glide.with(c.c)
                .load(url)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(h.b.icon)
            else h.b.icon.setImageDrawable(null)
        }

        // actions
        h.b.downloadAll.setOnClickListener {
            fetchHighlights(story, h.b.reel.adapter!! as ListRel, true)
        }

        // ListRel: initiation
        if (h.b.reel.adapter == null)
            h.b.reel.adapter = ListRel(c, f, story)
        else
            (h.b.reel.adapter!! as ListRel).story = story
        h.b.reel.adapter?.notifyDataSetChanged()

        // ListRel: open/close
        h.b.reel.scaleY = if (story.opened) 1f else 0f
        h.b.reel.layoutParams = h.b.reel.layoutParams.apply {
            height = if (story.opened) c.resources.getDimension(R.dimen.vwReelHeight).toInt() else 0
        }
        h.b.reel.vis(story.opened)
        if (story.opened && isHL)
            fetchHighlights(story, h.b.reel.adapter!! as ListRel)
        h.b.header.setOnClickListener {
            (story.anSlide as? ObjectAnimator)?.cancel()
            story.opened = !story.opened
            if (story.opened && isHL)
                fetchHighlights(story, h.b.reel.adapter!! as ListRel)
            ObjectAnimator.ofFloat(h.b.reel, View.SCALE_Y, if (story.opened) 1f else 0f).apply {
                story.anSlide = this
                addUpdateListener {
                    h.b.reel.layoutParams = h.b.reel.layoutParams.apply {
                        height = (c.resources.getDimension(R.dimen.vwReelHeight)
                            * it.animatedValue as Float).toInt()
                    }
                }
                addListener(
                    onStart = {
                        h.b.reel.vis(true)
                        if (story.opened) h.b.shadow.vis()
                    }, onEnd = {
                        h.b.reel.vis(story.opened)
                        if (!story.opened) h.b.shadow.vis(false)
                    }
                )
                start()
            }
        }

        h.b.line.vis(i < itemCount - 1)
    }

    override fun getItemCount(): Int =
        (if (c.mm.story != null) 1 else 0) +
            (c.mm.highlights?.edges?.size ?: 0)

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchHighlights(story: Story, listRel: ListRel, downloadAll: Boolean = false) {
        if (story.items != null && !downloadAll) return

        CoroutineScope(Dispatchers.IO).launch {
            if (story.items == null) {
                val apiId = "\"${story.id}\""
                val newStory = try {
                    Api.json<GraphQl>(
                        Api.Endpoint.QUERY.url, true, GraphQlQuery.HIGHLIGHTS.body(apiId, apiId)
                    ).data!!.xdt_api__v1__feed__reels_media__connection!!.edges.first().node
                } catch (e: Api.FailureException) {
                    withContext(Dispatchers.Main) {
                        UiTools.snackbar(f.b.root, UiTools.apiError(c.c, e.code))
                    }
                    return@launch
                }

                story.items = newStory.items
                c.mm.highlights?.also {
                    Pickle(c.c, Pickle.Type.HIGHLIGHTS, c.mm.user!!.id!!).save(it)
                }
            }

            if (downloadAll) {
                for (reel in story.items!!) reel.queue(c.dao, owner = c.mm.user!!)
                Downloads.initService(c)
            }

            withContext(Dispatchers.Main) {
                listRel.notifyDataSetChanged()
            }
        }
    }
}
