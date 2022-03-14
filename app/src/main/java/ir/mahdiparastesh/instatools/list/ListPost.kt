package ir.mahdiparastesh.instatools.list

import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.databinding.ListPostBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.GlideShimmer
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

abstract class ListPost<C, F>(protected val c: C, protected val f: F) :
    RecyclerView.Adapter<ListPost<C, F>.ViewHolder>() where C : BaseActivity, F : BasePage<C> {

    protected val typeVideo = c.drawable(R.drawable.video)!!
    protected val typeStack = c.drawable(R.drawable.stack)!!

    abstract val inflater: LayoutInflater
    abstract val tracker: SelectionTracker<String>?
    abstract val handler: Handler?
    abstract val expandable: Expandable
    abstract val expanded: ExpandableBinding

    inner class ViewHolder(val b: ListPostBinding) : RecyclerView.ViewHolder(b.root) {
        fun getItemDetails(): ItemDetailsLookup.ItemDetails<String> =
            object : ItemDetailsLookup.ItemDetails<String>() {
                override fun getPosition(): Int = layoutPosition
                override fun getSelectionKey(): String? = flexible(position)?.id
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListPostBinding.inflate(inflater, parent, false)
        b.root.layoutParams = b.root.layoutParams.apply {
            width = c.dm.widthPixels / 3
            height = c.dm.widthPixels / 3
        }
        return ViewHolder(b)
    }

    abstract class FlexiblePost(val id: String, val thumb: String?) {
        abstract fun typeDrw(): Drawable?
        abstract fun isStored(): Boolean
    }

    abstract fun flexible(i: Int): FlexiblePost?

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        val flex = flexible(i) ?: return

        if (flex.thumb != null) Glide.with(c.c)
            .load(flex.thumb)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .addListener(GlideShimmer(h.b.root, h.b.thumbnail))
            .into(h.b.thumbnail)

        h.b.type.setImageDrawable(flex.typeDrw())
        h.b.stored.vis(flex.isStored())

        h.b.click.setBackgroundResource(
            if (tracker?.isSelected(flex.id) != true) R.drawable.button else R.drawable.selected
        )
        h.b.click.setOnClickListener {
            expandable.settings(h.layoutPosition)
            expandable.thumb = it
            try {
                expandable.expand()
                f.jumper().vis(false)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) throw e
            }
        }
    }

    override fun onViewDetachedFromWindow(h: ViewHolder) {
        super.onViewDetachedFromWindow(h)
        Glide.with(c.c).clear(h.b.thumbnail)
    }

    abstract fun Expandable.settings(pos: Int)

    class PostDetailsLookup(private val rv: RecyclerView) : ItemDetailsLookup<String>() {
        override fun getItemDetails(e: MotionEvent): ItemDetails<String>? {
            rv.findChildViewUnder(e.x, e.y)?.let {
                val h = rv.getChildViewHolder(it)
                if (h is ListPost<*, *>.ViewHolder) return@getItemDetails h.getItemDetails()
            }
            return null
        }
    }

    // The selection tracker won't annihilate by settings its variable to null or destroying the
    // list adapter, if it is not annihilated, the selecting process will become messy and slow,
    // and the user won't be able to scroll while selection. If you can't annihilate it, so never
    // recreate it! And since the tracker is only created once, the adapter too must be created
    // only once!
}
