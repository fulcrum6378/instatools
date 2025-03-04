package ir.mahdiparastesh.instatools.view

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.facebook.shimmer.ShimmerFrameLayout
import ir.mahdiparastesh.instatools.api.Media

/*class MyGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.append(String::class.java, InputStream::class.java, StringLoader.StreamFactory())
        registry.append(Media::class.java, InputStream::class.java, MediaLoader.Factory())
    }
}

*/
/** Loads [Media] thumbnails assigning them their own unique cache keys. *//*
class MediaLoader : ModelLoader<Media, InputStream> {

    override fun handles(model: Media): Boolean = true
    override fun buildLoadData(
        model: Media, width: Int, height: Int, options: Options
    ): ModelLoader.LoadData<InputStream>? =
        ModelLoader.LoadData(
            ObjectKey(model.id()),
            HttpUrlFetcher(GlideUrl(model.thumb()), 5000)
        )

    class Factory : ModelLoaderFactory<Media, InputStream> {
        override fun teardown() {}
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Media, InputStream> =
            MediaLoader()
    }
}*/

/** Helper class for adding a shimmer effect on the [Glide] library during loading. */
class GlideShimmer(
    private val layout: ShimmerFrameLayout, private val image: ImageView,
    private val moreover: ((succeeded: Boolean) -> Unit)? = null
) : RequestListener<Drawable> {
    override fun onLoadFailed(
        e: GlideException?, model: Any?, target: Target<Drawable>,
        isFirstResource: Boolean
    ): Boolean {
        layout.stopShimmer()
        moreover?.let { it(false) }
        return false
    }

    override fun onResourceReady(
        resource: Drawable, model: Any, target: Target<Drawable>?,
        dataSource: DataSource, isFirstResource: Boolean
    ): Boolean {
        layout.hideShimmer()
        layout.stopShimmer()
        image.background = null
        moreover?.let { it(true) }
        return false
    }
}
