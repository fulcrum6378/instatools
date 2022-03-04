package ir.mahdiparastesh.instatools.list

import android.net.Uri
import android.view.ViewGroup
import androidx.media.AudioAttributesCompat
import androidx.media2.common.MediaMetadata
import androidx.media2.common.UriMediaItem
import androidx.media2.player.MediaPlayer
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.databinding.ListCarBinding
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Versioned
import ir.mahdiparastesh.instatools.serv.Queuer.MediaType
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

class ListCar(val c: BaseActivity, private val med: Media) :
    RecyclerView.Adapter<ListCar.ViewHolder>() {
    private val slides = arrayListOf<Slide>()
    val players: ArrayList<MediaPlayer?>

    init {
        if (med.carousel_media != null) for (slide in med.carousel_media) slides.add(
            Slide(
                slide.nearest(Versioned.BEST),
                MediaType.values().find { it.inDb == (slide.media_type).toInt().toByte() }!!
            )
        ) else if (med.image_versions2 != null) slides.add(
            Slide(
                med.nearest(Versioned.BEST),
                MediaType.values().find { it.inDb == (med.media_type).toInt().toByte() }!!
            )
        )
        players = ArrayList(arrayOfNulls<MediaPlayer?>(slides.size).toMutableList())
    }

    class ViewHolder(val b: ListCarBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListCarBinding.inflate(c.layoutInflater, parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        h.b.image.vis(false)
        h.b.video.vis(false)
        h.b.image.setImageDrawable(null)
        players[i]?.close()
        players[i] = null
        players.forEachIndexed { ii, mp -> if (i != ii) mp?.pause() }

        when (slides[i].type) {
            MediaType.PHOTO -> slides[i].url?.let {
                h.b.image.vis()
                Glide.with(c.c).load(it).into(h.b.image)
            }
            MediaType.VIDEO -> slides[i].url?.let {
                h.b.video.vis()
                players[i] = MediaPlayer(c).apply {
                    setAudioAttributes(
                        AudioAttributesCompat.Builder()
                            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                            .build()
                    )
                    setMediaItem(
                        UriMediaItem.Builder(Uri.parse(it)).setMetadata(
                            MediaMetadata.Builder()
                                .putString(MediaMetadata.METADATA_KEY_TITLE, med.user.visName())
                                .build()
                        ).build()
                    )
                    h.b.video.setPlayer(this)
                    prepare()
                }
            }
        }
    }

    override fun getItemCount() = slides.size

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        players.forEach { it?.close() }
    }

    data class Slide(val url: String?, val type: MediaType)
}
