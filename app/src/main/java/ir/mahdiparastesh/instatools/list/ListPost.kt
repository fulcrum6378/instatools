package ir.mahdiparastesh.instatools.list

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
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.GlideShimmer

abstract class ListPost<C>(protected val c: C) :
    RecyclerView.Adapter<ListPost<C>.ViewHolder>() where C : BaseActivity {

    private val typeVideo = c.drawable(R.drawable.video)!!
    private val typeStack = c.drawable(R.drawable.stack)!!

    abstract val edges: ArrayList<Profile.EdgePost>?
    abstract val inflater: LayoutInflater
    abstract val tracker: SelectionTracker<String>?
    abstract val handler: Handler?
    abstract val expandable: Expandable
    abstract val expanded: ExpandableBinding

    inner class ViewHolder(val b: ListPostBinding) : RecyclerView.ViewHolder(b.root) {
        fun getItemDetails(): ItemDetailsLookup.ItemDetails<String> =
            object : ItemDetailsLookup.ItemDetails<String>() {
                override fun getPosition(): Int = layoutPosition
                override fun getSelectionKey(): String? = edges?.getOrNull(position)?.node?.id
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

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        val node = edges?.getOrNull(i)?.node ?: return

        Glide.with(c.c)
            .load(node.thumbnail_src)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .addListener(GlideShimmer(h.b.root, h.b.thumbnail))
            .into(h.b.thumbnail)

        h.b.type.setImageDrawable(
            when (node.__typename) {
                "GraphSidecar" -> typeStack
                "GraphVideo" -> typeVideo
                "GraphImage" -> null
                else -> null
            }
        )

        h.b.click.setBackgroundResource(
            if (tracker?.isSelected(node.id) != true) R.drawable.button else R.drawable.selected
        )
        h.b.click.setOnClickListener {
            expandable.node = edges?.getOrNull(h.layoutPosition)?.node ?: return@setOnClickListener
            expandable.thumb = it
            try {
                expandable.expand()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) throw e
            }
        }
    }

    override fun getItemCount() = edges?.size ?: 0

    class PostDetailsLookup(private val rv: RecyclerView) : ItemDetailsLookup<String>() {
        override fun getItemDetails(e: MotionEvent): ItemDetails<String>? {
            rv.findChildViewUnder(e.x, e.y)?.let {
                val h = rv.getChildViewHolder(it)
                if (h is ListPost<*>.ViewHolder) return@getItemDetails h.getItemDetails()
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
