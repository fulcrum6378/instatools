package ir.mahdiparastesh.instatools.list

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.databinding.ListPostBinding
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.BasePage
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.GlideShimmer
import ir.mahdiparastesh.instatools.view.UiTools.vis

/** Abstract [RecyclerView.Adapter] that lists Instagram [Media]s in a grid. */
abstract class ListPost<Activity, Fragment>(
    protected val c: Activity, protected val f: Fragment
) : RecyclerView.Adapter<AnyViewHolder<ListPostBinding>>()
    where Activity : BaseActivity, Fragment : BasePage<Activity> {

    init {
        setHasStableIds(true)
    }

    protected val typeVideo = c.drawable(R.drawable.video)!!
    protected val typeStack = c.drawable(R.drawable.stack)!!
    var firstLongClickSelect = false

    abstract val inflater: LayoutInflater
    abstract val tracker: SelectionTracker<String>?
    abstract val expandable: Expandable
    abstract val expanded: ExpandableBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int)
        : AnyViewHolder<ListPostBinding> {
        val b = ListPostBinding.inflate(inflater, parent, false)
        b.root.layoutParams = b.root.layoutParams.apply {
            width = c.c.dm.widthPixels / 3
            height = c.c.dm.widthPixels / 3
        }
        return AnyViewHolder<ListPostBinding>(b)
    }

    /**
     * IMPORTANT NOTE: Do NOT use `layoutPosition` here;
     * use `bindingAdapterPosition` instead in order to support the positioning of ConcatAdapter.
     */
    override fun onBindViewHolder(h: AnyViewHolder<ListPostBinding>, i: Int) {
        val med = this[i] ?: return
        val norm = tracker?.isSelected(med.id()) != true

        // load thumbnail
        if (med.thumb() != null) Glide.with(c.c)
            .load(med.thumb())
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .signature(ObjectKey(med.id()))
            .centerCrop()
            .addListener(GlideShimmer(h.b.root, h.b.thumbnail))
            .into(h.b.thumbnail)

        // media type
        h.b.type.setImageDrawable(
            when {
                med.carousel_media != null -> typeStack
                med.video_versions != null -> typeVideo
                else -> null
            }
        )

        // is media already downloaded?
        val theirs = c.c.downloadHistory?.filter { it.startsWith("${med.owner().username}_") }
            ?.map { it.substringBeforeLast(".").substringAfterLast("_") }
        h.b.stored.vis(
            if (theirs == null)
                false
            else if (med.carousel_media != null)
                med.carousel_media!!.any { it.id() in theirs }
            else
                med.id() in theirs
        )

        // is media liked?
        h.b.liked.vis(med.has_liked == true)

        // is media saved?
        h.b.saved.vis(this !is ListSvd && med.has_viewer_saved == true)

        // is media selected?
        h.b.click.setBackgroundResource(if (norm) R.drawable.button else R.drawable.selected)

        // clicks
        h.b.click.setOnClickListener {
            expand(it, h.bindingAdapterPosition)
        }
        h.b.click.setOnLongClickListener {
            if (firstLongClickSelect) {
                firstLongClickSelect = false
                return@setOnLongClickListener false
            }
            expand(it, h.bindingAdapterPosition)
            true
        }
    }

    override fun getItemId(position: Int): Long =
        this[position]?.uid ?: RecyclerView.NO_ID

    abstract operator fun get(position: Int): Media?

    private fun expand(v: View, i: Int) {
        expandable.expand(this[i], v)
    }

    class PostDetailsLookup(private val rv: RecyclerView) : ItemDetailsLookup<String>() {

        override fun getItemDetails(e: MotionEvent): ItemDetails<String>? {
            val view = rv.findChildViewUnder(e.x, e.y) ?: return null
            val h = rv.getChildViewHolder(view) as AnyViewHolder<*>
            return if (rv.adapter is ListPost<*, *>)
                object : ItemDetails<String>() {
                    private val adapter = rv.adapter as ListPost<*, *>
                    override fun getPosition(): Int = h.layoutPosition
                    override fun getSelectionKey(): String? = adapter[position]?.id()
                }
            else
                object : ItemDetails<String>() {
                    private val adapter =
                        (rv.adapter as ConcatAdapter).adapters[1] as ListPost<*, *>

                    override fun getPosition(): Int = h.bindingAdapterPosition
                    override fun getSelectionKey(): String? = adapter[position]?.id()
                }
        }
    }

    // The selection tracker won't annihilate by settings its variable to null or destroying the
    // list adapter, if it is not annihilated, the selecting process will become messy and slow,
    // and the user won't be able to scroll while selection. If you can't annihilate it, so never
    // recreate it! And since the tracker is only created once, the adapter too must be created
    // only once!
}
