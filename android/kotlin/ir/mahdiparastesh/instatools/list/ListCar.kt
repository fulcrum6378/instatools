package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C.USAGE_MEDIA
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.databinding.ListCarBinding
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools.vis

class ListCar(
    val c: BaseActivity,
    private val med: Media,
    private val muteSound: MutableLiveData<Boolean>
) : RecyclerView.Adapter<AnyViewHolder<ListCarBinding>>() {
    private val slides = arrayListOf<Slide>()
    val sessions: ArrayList<MediaSession?>

    init {
        if (med.carousel_media != null) for (slide in med.carousel_media) slides.add(
            Slide(
                slide.nearest(),
                Media.Type.entries.find { it.num == (slide.media_type).toInt().toByte() }!!
            )
        ) else slides.add(
            Slide(
                med.nearest(),
                Media.Type.entries.find { it.num == (med.media_type).toInt().toByte() }!!
            )
        )
        sessions = ArrayList(arrayOfNulls<MediaSession?>(slides.size).toMutableList())
    }
    /* Do not use thumbnails for Expandable, they're cropped! */

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListCarBinding> =
        AnyViewHolder(ListCarBinding.inflate(c.layoutInflater, parent, false))

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
                Glide.with(c.c)
                    .load(it)
                    .timeout(10000)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(h.b.image)
            }
            Media.Type.VIDEO -> slides[i].url?.also {
                h.b.video.vis()
                sessions[i] = MediaSession.Builder(
                    c, ExoPlayer.Builder(c).setAudioAttributes(
                        AudioAttributes.Builder().setUsage(USAGE_MEDIA).build(), false
                    ).build()
                ).setId(med.id()).build().apply {
                    player.setMediaItem(MediaItem.fromUri(it.toUri()))
                    player.volume = if (muteSound.value == true) 0f else 1f
                    h.b.video.setPlayer(player)
                    player.prepare()
                }

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
}
