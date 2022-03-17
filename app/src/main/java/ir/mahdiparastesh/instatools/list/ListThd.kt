package ir.mahdiparastesh.instatools.list

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListThdBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.more.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.more.Versioned
import ir.mahdiparastesh.instatools.serv.Exporter.Downloadable
import ir.mahdiparastesh.instatools.view.UiTools.Companion.PROFILE
import ir.mahdiparastesh.instatools.view.UiTools.Companion.anchor
import ir.mahdiparastesh.instatools.view.UiTools.Companion.calendar
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.xFromMicroseconds
import ir.mahdiparastesh.instatools.view.UiTools.Companion.z
import java.util.*

class ListThd(val c: Main, private val f: PageBox) : RecyclerView.Adapter<ListThd.ViewHolder>() {
    private val idealW = (c.dm.widthPixels.toFloat() * 0.8f) * c.dm.density

    class ViewHolder(val b: ListThdBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ListThdBinding.inflate(f.inflater, parent, false).onCreate(c.fontRegular, c.fontLight)
    )

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.dmThread != null) h.b.onBind(c.c, c.m.dmThread!!.items, i, idealW, f, h)
    }

    override fun getItemCount() = c.m.dmThread?.items?.size ?: 0

    companion object {
        fun ListThdBinding.onCreate(
            fontRegular: Typeface, fontLight: Typeface, isExporting: Boolean = false
        ): ListThdBinding {
            date.typeface = fontLight
            msgTv.typeface = fontRegular
            time.typeface = fontLight
            if (isExporting) msgIvCl.removeView(msgLoading)
            return this
        }

        @SuppressLint("CheckResult", "SetTextI18n")
        fun ListThdBinding.onBind(
            c: Context, list: List<Dm>, i: Int, idealW: Float = Versioned.BEST,
            f: PageBox? = null, h: ViewHolder? = null,
            downloaded: HashMap<String, Downloadable>? = null,
        ): ListThdBinding {
            val dm = list.getOrNull(i) ?: return this
            body.vis(dm.action_log == null)

            // Layout
            area.layoutParams = (area.layoutParams as ConstraintLayout.LayoutParams).apply {
                horizontalBias = if (dm.is_sent_by_viewer) 1f else 0f
            }
            message.layoutParams =
                (message.layoutParams as ConstraintLayout.LayoutParams).apply {
                    horizontalBias = if (dm.is_sent_by_viewer) 1f else 0f
                }
            reactions.layoutParams =
                (reactions.layoutParams as ConstraintLayout.LayoutParams).apply {
                    horizontalBias = if (dm.is_sent_by_viewer) 1f else 0f
                }
            time.layoutParams =
                (time.layoutParams as ConstraintLayout.LayoutParams).apply {
                    if (dm.is_sent_by_viewer) {
                        startToEnd = ConstraintLayout.LayoutParams.UNSET
                        endToStart = message.id
                    } else {
                        startToEnd = message.id
                        endToStart = ConstraintLayout.LayoutParams.UNSET
                    }
                }
            message.setBackgroundResource(
                if (dm.is_sent_by_viewer) R.drawable.dm_from_me else R.drawable.dm_to_me
            )
            message.textAlignment =
                if (dm.is_sent_by_viewer) TextView.TEXT_ALIGNMENT_VIEW_END
                else TextView.TEXT_ALIGNMENT_VIEW_START

            // Date
            val cal = dm.timestamp.xFromMicroseconds().calendar()
            var showDate = true
            if (i > 0) {
                val prev = list[i - 1].timestamp.xFromMicroseconds().calendar()
                if (cal[Calendar.YEAR] == prev[Calendar.YEAR] &&
                    cal[Calendar.MONTH] == prev[Calendar.MONTH] &&
                    cal[Calendar.DAY_OF_MONTH] == prev[Calendar.DAY_OF_MONTH]
                ) showDate = false
            }
            date.vis(showDate)
            if (showDate) date.text =
                "${cal[Calendar.YEAR]}.${z(cal[Calendar.MONTH] + 1)}.${z(cal[Calendar.DAY_OF_MONTH])}"

            // Message
            msgTv.anchor(null, null)
            msgIvHint.vis(false)
            var media: Media? = null
            when {
                dm.action_log != null ->
                    msgIvHint.apply { text = dm.action_log.description; vis() }
                dm.clip != null -> media = dm.clip.clip
                dm.felix_share != null -> {
                    media = dm.felix_share.video
                    if (dm.felix_share.message != null)
                        msgIvHint.apply { text = dm.felix_share.message; vis() }
                    msgTv.text = dm.felix_share.text
                }
                dm.link != null -> msgTv.anchor(dm.link.text, dm.link.link_context.link_url)
                dm.media != null -> media = dm.media
                dm.media_share != null -> media = dm.media_share
                dm.profile != null ->
                    msgTv.anchor("@${dm.profile.username}", PROFILE.format(dm.profile.username))
                dm.reel_share != null -> {
                    media = dm.reel_share.media
                    if (dm.reel_share.message != null)
                        msgIvHint.apply { text = dm.reel_share.message; vis() }
                    msgTv.text = dm.reel_share.text
                    //media?.mahdi_reel_type = dm.reel_share.reel_type
                }
                dm.story_share != null -> {
                    media = dm.story_share.media
                    if (dm.story_share.message != null)
                        msgIvHint.apply { text = dm.story_share.message; vis() }
                    msgTv.text = dm.story_share.text
                    media?.mahdi_reel_type = dm.story_share.reel_type
                    media?.mahdi_reel_id = dm.story_share.reel_id
                }
                dm.text != null -> msgTv.text = dm.text
                dm.video_call_event != null ->
                    msgIvHint.apply { text = dm.video_call_event.description; vis() }
                dm.voice_media != null -> {
                    msgIvHint.apply { text = "Voice"; vis() }
                }
                dm.placeholder != null -> msgIvHint.apply { text = dm.placeholder.message; vis() }
                //else -> b.msgTv.text = dm.item_type
            }
            msgIvCl.vis(media != null)
            Glide.with(c).clear(msgIv)
            msgIv.setImageDrawable(null)
            media?.apply {
                if (carousel_media == null && image_versions2 == null) return@apply
                var data: ByteArray? = null
                if (downloaded != null)
                    data = downloaded.getOrDefault(dm.item_id, null)?.data
                if (data != null && data.isNotEmpty()) {
                    msgIv.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.size))
                    return@apply; }
                if (downloaded != null) return@apply

                msgLoading.apply {
                    setAnimation(if (!c.night()) R.raw.pending_tertiary else R.raw.pending)
                    playAnimation()
                    vis()
                }
                Glide.with(c).load(
                    carousel_media?.getOrNull(0)?.nearest(idealW) ?: nearest(idealW)
                ).diskCacheStrategy(DiskCacheStrategy.ALL).into(
                    object : CustomTarget<Drawable>() {
                        override fun onLoadCleared(placeholder: Drawable?) {}
                        override fun onResourceReady(
                            res: Drawable, trans: Transition<in Drawable>?
                        ) {
                            if (h != null && h.layoutPosition != i) return
                            msgLoading.pauseAnimation()
                            msgLoading.vis(false)
                            msgIv.layoutParams = msgIv.layoutParams.apply {
                                width = WRAP_CONTENT
                                height = WRAP_CONTENT
                            }
                            msgIv.setImageDrawable(res)
                        }
                    })
            }
            if (downloaded == null) mediaClick.setOnClickListener {
                if (f?.expandable == null) return@setOnClickListener
                f.expandable.media = media
                f.expandable.thumb = root
                try {
                    f.expandable.expand()
                    f.jumper().vis(false)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) throw e
                }
            }
            msgTv.vis(msgTv.text.isNotBlank())

            // Reactions
            reactions.removeAllViews()
            reactions.vis(dm.reactions != null)
            if (dm.reactions != null) for (r in dm.reactions.emojis) reactions.addView(
                TextView(c).apply {
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                    text = r.emoji
                }
            )

            // Time
            time.text =
                "${z(cal[Calendar.HOUR_OF_DAY])}:${z(cal[Calendar.MINUTE])}:${z(cal[Calendar.SECOND])}"

            return this
        }
    }
}
