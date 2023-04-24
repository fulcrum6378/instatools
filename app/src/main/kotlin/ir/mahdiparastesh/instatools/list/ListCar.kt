package ir.mahdiparastesh.instatools.list

import android.net.Uri
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioAttributesCompat
import androidx.media2.common.MediaMetadata
import androidx.media2.common.UriMediaItem
import androidx.media2.player.MediaPlayer
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.databinding.ListCarBinding
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.serv.Queuer.MediaType
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools.vis

class ListCar(
    val c: BaseActivity,
    private val med: Media,
    private val muteSound: MutableLiveData<Boolean>
) : RecyclerView.Adapter<AnyViewHolder<ListCarBinding>>() {
    private val slides = arrayListOf<Slide>()
    val players: ArrayList<MediaPlayer?>

    init {
        val quality =
            (if (med.product_type == "story") c.dm.heightPixels
            else arrayOf(c.dm.widthPixels, c.dm.heightPixels).min()).toFloat()
        if (med.carousel_media != null) for (slide in med.carousel_media!!) slides.add(
            Slide(
                slide.nearest(quality),
                MediaType.values().find { it.inDb == (slide.media_type).toInt().toByte() }!!
            )
        ) else if (med.image_versions2 != null) slides.add(
            Slide(
                med.nearest(quality),
                MediaType.values().find { it.inDb == (med.media_type).toInt().toByte() }!!
            )
        )
        players = ArrayList(arrayOfNulls<MediaPlayer?>(slides.size).toMutableList())
    }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListCarBinding> =
        AnyViewHolder(ListCarBinding.inflate(c.layoutInflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListCarBinding>, i: Int) {
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
                                .putString(MediaMetadata.METADATA_KEY_TITLE, "")
                                .build()
                        ).build()
                    )
                    playerVolume = if (muteSound.value == true) 0f else 1f
                    h.b.video.setPlayer(this)
                    prepare()
                }
            }
            else -> { // IMPOSSIBLE
            }
        }
    }

    override fun getItemCount() = slides.size

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        players.forEach { it?.close() }
    }

    data class Slide(val url: String?, val type: MediaType)
}
