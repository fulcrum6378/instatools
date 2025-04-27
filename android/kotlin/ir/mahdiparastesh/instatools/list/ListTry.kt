package ir.mahdiparastesh.instatools.list

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.animation.addListener
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.databinding.ListStoBinding
import ir.mahdiparastesh.instatools.frag.PageTry
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListTry(private val c: Main, private val f: PageTry) :
    RecyclerView.Adapter<AnyViewHolder<ListStoBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListStoBinding> =
        AnyViewHolder(ListStoBinding.inflate(f.inflater, parent, false))

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(h: AnyViewHolder<ListStoBinding>, i: Int) {
        val story = c.vm.tray?.tray?.getOrNull(i) ?: return

        // user details
        val user = story.user
        Glide.with(c.c)
            .load(user.profile_pic_url)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(h.b.icon)
        h.b.title.text = "${i + 1}. ${user.username}"  // user.full_name is always null!
        h.b.desc.text = if (!story.items.isNullOrEmpty()) c.resources.getQuantityString(
            R.plurals.vwReelDesc, story.items!!.size, story.items!!.size
        ) else ""

        // actions
        h.b.downloadAll.setOnClickListener {
            fetchStory(story, h.layoutPosition, h.b.reel.adapter!! as ListStory, true)
        }

        // ListStory: initiation
        if (h.b.reel.adapter == null)
            h.b.reel.adapter = ListStory(c.c, f.inflater, f.expandable, story)
        else {
            (h.b.reel.adapter!! as ListStory).story = story
            h.b.reel.adapter?.notifyDataSetChanged()
        }

        // ListStory: open/close
        h.b.reel.scaleY = if (story.opened) 1f else 0f
        h.b.reel.layoutParams = h.b.reel.layoutParams.apply {
            height = if (story.opened) c.resources.getDimension(R.dimen.vwReelHeight).toInt() else 0
        }
        h.b.reel.vis(story.opened)
        if (story.opened)
            fetchStory(story, h.layoutPosition, h.b.reel.adapter!! as ListStory)
        h.b.header.setOnClickListener {
            (story.anSlide as? ObjectAnimator)?.cancel()
            story.opened = !story.opened
            if (story.opened)
                fetchStory(story, h.layoutPosition, h.b.reel.adapter!! as ListStory)
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

    override fun getItemCount(): Int = c.vm.tray?.tray?.size ?: 0

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchStory(
        story: Story,
        i: Int,
        listStory: ListStory,
        downloadAll: Boolean = false
    ) {
        if (story.items != null && !downloadAll) return

        CoroutineScope(Dispatchers.IO).launch {
            if (story.items == null) {
                val newStory = try {
                    Api.json<GraphQl>(
                        Api.Endpoint.QUERY.url, true, GraphQlQuery.STORY.body(story.user.id())
                    ).data!!.xdt_api__v1__feed__reels_media!!.reels_media.firstOrNull()
                } catch (e: Api.FailureException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(c, UiTools.apiError(c.c, e.code), Toast.LENGTH_LONG).show()
                        //UiTools.snackbar(f.b.root, UiTools.apiError(c.c, e.code))
                    }
                    return@launch
                }

                story.items = newStory?.items
                c.vm.tray?.also { f.pickle.save(it) }
            }

            if (downloadAll) {
                for (reel in story.items!!) c.c.downloads.addAll<Download>(
                    reel.queue(owner = story.user.username), false
                )
                c.c.downloads.save<Download>()
                Downloads.initService(c)
            }

            withContext(Dispatchers.Main) {
                this@ListTry.notifyItemChanged(i)
                listStory.notifyDataSetChanged()
            }
        }
    }
}
