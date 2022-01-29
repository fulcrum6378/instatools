package ir.mahdiparastesh.instatools.list

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListSvdBinding
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.UiTools

class ListSvd(val c: Main, val f: PageSvd) : RecyclerView.Adapter<ListSvd.ViewHolder>() {
    var zoomed = false
    private var currentAnimator: Animator? = null
    private var startScale: Float? = null
    private var startBounds: RectF? = null
    private var zoomedThumb: View? = null

    inner class ViewHolder(val b: ListSvdBinding) : RecyclerView.ViewHolder(b.root) {
        fun getItemDetails(): ItemDetailsLookup.ItemDetails<String?> =
            object : ItemDetailsLookup.ItemDetails<String?>() {
                override fun getPosition(): Int = layoutPosition
                override fun getSelectionKey(): String? = c.m.saved?.get(position)?.id
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListSvdBinding
            .inflate(c.themeInflater(BaseActivity.Theme.SECONDARY), parent, false)
        b.root.layoutParams = b.root.layoutParams.apply {
            width = c.dm.widthPixels / 3
            height = c.dm.widthPixels / 3
        }
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.saved == null) return

        Glide.with(c.c)
            .load(c.m.saved!![i].thumbnail_src)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    h.b.root.stopShimmer()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?, model: Any?, target: Target<Drawable>?,
                    dataSource: DataSource?, isFirstResource: Boolean
                ): Boolean {
                    h.b.root.hideShimmer()
                    h.b.root.stopShimmer()
                    h.b.thumbnail.background = null
                    return false
                }
            })
            .into(h.b.thumbnail)

        if (f.tracker != null) h.b.click.setBackgroundResource(
            if (!f.tracker!!.isSelected(c.m.saved!![i].id)) R.drawable.button else R.drawable.selected
        )
        h.b.click.setOnClickListener {
            zoomedThumb = it
            try {
                zoomImageFromThumb(c.m.saved!![h.layoutPosition].display_url)
            } catch (ignored: NullPointerException) {
            }
        }
        h.b.click.setOnLongClickListener {
            h.b.root.isActivated = true
            UiTools.shake(c.c)
            true
        }
    }

    override fun getItemCount() = c.m.saved?.size ?: 0

    private fun zoomImageFromThumb(url: String) {
        if (zoomedThumb == null || zoomed) return
        zoomed = true
        currentAnimator?.cancel()
        Glide.with(c.c).load(url).into(f.b.expanded)
        val startBoundsInt = Rect()
        val finalBoundsInt = Rect()
        val globalOffset = Point()

        zoomedThumb?.getGlobalVisibleRect(startBoundsInt)
        f.b.root.getGlobalVisibleRect(finalBoundsInt, globalOffset)
        startBoundsInt.offset(-globalOffset.x, -globalOffset.y)
        finalBoundsInt.offset(-globalOffset.x, -globalOffset.y)

        startBounds = RectF(startBoundsInt)
        val finalBounds = RectF(finalBoundsInt)

        if ((finalBounds.width() / finalBounds.height() > startBounds!!.width() / startBounds!!.height())) {
            startScale = startBounds!!.height() / finalBounds.height()
            val startWidth: Float = startScale!! * finalBounds.width()
            val deltaWidth: Float = (startWidth - startBounds!!.width()) / 2
            startBounds!!.left -= deltaWidth.toInt()
            startBounds!!.right += deltaWidth.toInt()
        } else {
            startScale = startBounds!!.width() / finalBounds.width()
            val startHeight: Float = startScale!! * finalBounds.height()
            val deltaHeight: Float = (startHeight - startBounds!!.height()) / 2f
            startBounds!!.top -= deltaHeight.toInt()
            startBounds!!.bottom += deltaHeight.toInt()
        }

        zoomedThumb!!.alpha = 0f
        f.b.expanded.visibility = View.VISIBLE
        f.b.expanded.pivotX = 0f
        f.b.expanded.pivotY = 0f

        currentAnimator = AnimatorSet().apply {
            play(
                ObjectAnimator.ofFloat(f.b.expanded, View.X, startBounds!!.left, finalBounds.left)
            ).apply {
                with(
                    ObjectAnimator.ofFloat(f.b.expanded, View.Y, startBounds!!.top, finalBounds.top)
                )
                with(ObjectAnimator.ofFloat(f.b.expanded, View.SCALE_X, startScale!!, 1f))
                with(ObjectAnimator.ofFloat(f.b.expanded, View.SCALE_Y, startScale!!, 1f))
                with(
                    ObjectAnimator.ofArgb(
                        f.b.expanded, "backgroundColor", c.color(R.color.tp),
                        c.colorBG.value ?: c.color(R.color.defBG)
                    )
                )
            }
            duration = zoomDur
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    currentAnimator = null
                }
            })
            start()
        }

        f.b.expanded.setOnClickListener { collapse() }
    }

    fun collapse() {
        if (startBounds == null || startScale == null || !zoomed) return
        currentAnimator?.cancel()
        currentAnimator = AnimatorSet().apply {
            play(ObjectAnimator.ofFloat(f.b.expanded, View.X, startBounds!!.left)).apply {
                with(ObjectAnimator.ofFloat(f.b.expanded, View.Y, startBounds!!.top))
                with(ObjectAnimator.ofFloat(f.b.expanded, View.SCALE_X, startScale!!))
                with(ObjectAnimator.ofFloat(f.b.expanded, View.SCALE_Y, startScale!!))
            }
            duration = zoomDur
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    zoomedThumb?.alpha = 1f
                    zoomedThumb = null
                    f.b.expanded.visibility = View.GONE
                    f.b.expanded.setImageBitmap(null)
                    currentAnimator = null
                    zoomed = false
                }

                override fun onAnimationCancel(animation: Animator) {
                    zoomedThumb?.alpha = 1f
                    zoomedThumb = null
                    f.b.expanded.visibility = View.GONE
                    currentAnimator = null
                    zoomed = false
                }
            })
            start()
        }
    }

    companion object {
        const val zoomDur = 200L
    }
}
