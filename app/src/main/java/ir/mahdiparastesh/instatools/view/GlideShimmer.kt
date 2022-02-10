package ir.mahdiparastesh.instatools.view

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.facebook.shimmer.ShimmerFrameLayout

class GlideShimmer(
    private val layout: ShimmerFrameLayout, private val image: ImageView,
    private val moreover: ((succeeded: Boolean) -> Unit)? = null
) : RequestListener<Drawable> {
    override fun onLoadFailed(
        e: GlideException?, model: Any?, target: Target<Drawable>?,
        isFirstResource: Boolean
    ): Boolean {
        layout.stopShimmer()
        moreover?.let { it(false) }
        return false
    }

    override fun onResourceReady(
        resource: Drawable?, model: Any?, target: Target<Drawable>?,
        dataSource: DataSource?, isFirstResource: Boolean
    ): Boolean {
        layout.hideShimmer()
        layout.stopShimmer()
        image.background = null
        moreover?.let { it(true) }
        return false
    }
}
