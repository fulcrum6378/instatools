package ir.mahdiparastesh.instatools.list

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C.USAGE_MEDIA
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.databinding.ListCarBinding
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish

class ListCar(
    private val x: Expandable,
) : RecyclerView.Adapter<AnyViewHolder<ListCarBinding>>() {
    private val slides = arrayListOf<Slide>()
    val sessions: ArrayList<MediaSession?>
    var loading = true

    init {
        if (x.media!!.carousel_media != null)
            for (slide in x.media!!.carousel_media) slides.add(
                Slide(
                    slide.nearest(),
                    Media.Type.entries.find { it.num == (slide.media_type).toInt().toByte() }!!
                )
            )
        else slides.add(
            Slide(
                x.media!!.nearest(),
                Media.Type.entries
                    .find { it.num == (x.media!!.media_type).toInt().toByte() }!!
            )
        )
        sessions = ArrayList(arrayOfNulls<MediaSession?>(slides.size).toMutableList())
    }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListCarBinding> =
        AnyViewHolder(ListCarBinding.inflate(x.c.layoutInflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListCarBinding>, i: Int) {
        h.b.image.vis(false)
        h.b.video.vis(false)
        h.b.image.setImageDrawable(null)
        sessions[i]?.player?.release()
        sessions[i]?.release()
        sessions[i] = null
        sessions.forEachIndexed { ii, ms -> if (i != ii) ms?.player?.pause() }

        when (slides[i].type) {
            Media.Type.IMAGE -> slides[i].url?.also {
                h.b.image.vis()
                Glide.with(x.c)
                    .load(it)
                    .timeout(10000)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .listener(OnImageLoadedListener(/*h.b.root*/))
                    .into(h.b.image)
            }
            Media.Type.VIDEO -> slides[i].url?.also {
                h.b.video.vis()
                sessions[i] = MediaSession.Builder(
                    x.c, ExoPlayer.Builder(x.c).setAudioAttributes(
                        AudioAttributes.Builder().setUsage(USAGE_MEDIA).build(), false
                    ).build()
                ).setId(x.media!!.id()).build().apply {
                    player.setMediaItem(MediaItem.fromUri(it.toUri()))
                    player.volume = if (x.muteSound.value == true) 0f else 1f
                    h.b.video.setPlayer(player)
                    player.prepare()
                }
                loading = false
            }
            else -> { // IMPOSSIBLE
            }
        }
    }

    override fun getItemCount() = slides.size

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        sessions.forEach {
            it?.player?.release()
            it?.release()
        }
    }

    data class Slide(val url: String?, val type: Media.Type)

    inner class OnImageLoadedListener/*(private val root: ViewGroup)*/ : RequestListener<Drawable> {

        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable?>,
            isFirstResource: Boolean
        ): Boolean {
            if (x.currentAnimator != null) return true
            //UiTools.snackbar(root, R.string.couldNotLoadOriginal, dur = Snackbar.LENGTH_SHORT)
            Toast.makeText(x.c, R.string.couldNotLoadOriginal, Toast.LENGTH_SHORT).show()
            loading = false
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable?>?,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            if (x.currentAnimator != null) return false
            x.b.thumb.vish(false)
            loading = false
            return false
        }
    }
}
