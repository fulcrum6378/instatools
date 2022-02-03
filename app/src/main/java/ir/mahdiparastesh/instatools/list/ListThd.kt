package ir.mahdiparastesh.instatools.list

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListThdBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.view.UiTools.Companion.PROFILE
import ir.mahdiparastesh.instatools.view.UiTools.Companion.anchor
import ir.mahdiparastesh.instatools.view.UiTools.Companion.calendar
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.z
import java.util.*

class ListThd(val c: Main, private val f: PageBox) : RecyclerView.Adapter<ListThd.ViewHolder>() {
    class ViewHolder(val b: ListThdBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListThdBinding.inflate(f.inflater, parent, false)
        onCreate(b, c.fontRegular, c.fontLight)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.dmThread != null)
            onBind(c.c, h.b, c.m.dmThread!!.items[i], c.m.dmThread!!.items, i)
    }

    override fun getItemCount() = c.m.dmThread?.items?.size ?: 0

    companion object {
        fun onCreate(b: ListThdBinding, fontRegular: Typeface, fontLight: Typeface) {
            b.date.typeface = fontLight
            b.msgTv.typeface = fontRegular
            b.time.typeface = fontLight
        }

        @SuppressLint("SetTextI18n")
        fun onBind(c: Context, b: ListThdBinding, dm: Dm, list: List<Dm>, i: Int) {

            // Layout
            b.area.layoutParams = (b.area.layoutParams as ConstraintLayout.LayoutParams).apply {
                horizontalBias = if (dm.is_sent_by_viewer) 1f else 0f
            }
            b.message.layoutParams =
                (b.message.layoutParams as ConstraintLayout.LayoutParams).apply {
                    horizontalBias = if (dm.is_sent_by_viewer) 1f else 0f
                }
            b.time.layoutParams =
                (b.time.layoutParams as ConstraintLayout.LayoutParams).apply {
                    if (dm.is_sent_by_viewer) {
                        startToEnd = ConstraintLayout.LayoutParams.UNSET
                        endToStart = b.message.id
                    } else {
                        startToEnd = b.message.id
                        endToStart = ConstraintLayout.LayoutParams.UNSET
                    }
                }
            b.message.setBackgroundResource(
                if (dm.is_sent_by_viewer) R.drawable.dm_from_me else R.drawable.dm_to_me
            )
            b.message.textAlignment =
                if (dm.is_sent_by_viewer) TextView.TEXT_ALIGNMENT_VIEW_END
                else TextView.TEXT_ALIGNMENT_VIEW_START

            // Date
            val cal = calendar(dm.timestamp)
            var showDate = true
            if (i > 0) {
                val prev = calendar(list[i - 1].timestamp)
                if (cal[Calendar.YEAR] == prev[Calendar.YEAR] &&
                    cal[Calendar.MONTH] == prev[Calendar.MONTH] &&
                    cal[Calendar.DAY_OF_MONTH] == prev[Calendar.DAY_OF_MONTH]
                ) showDate = false
            }
            b.date.vis(showDate)
            if (showDate) b.date.text =
                "${cal[Calendar.YEAR]}.${z(cal[Calendar.MONTH] + 1)}.${z(cal[Calendar.DAY_OF_MONTH])}"

            // Message
            b.msgTv.anchor(null, null)
            var media: Media? = null
            when {
                dm.action_log != null -> b.msgTv.text = dm.action_log.description
                dm.clip != null -> media = dm.clip.clip
                dm.felix_share != null -> {
                    media = dm.felix_share.video
                    /*if (dm.felix_share.message == null)
                        b.msgIv.contentDescription = dm.felix_share.message*/
                    b.msgTv.text = dm.felix_share.text
                }
                dm.link != null -> b.msgTv.anchor(dm.link.text, dm.link.link_context.link_url)
                dm.media != null -> media = dm.media
                dm.media_share != null -> media = dm.media_share
                dm.profile != null ->
                    b.msgTv.anchor("@${dm.profile.username}", PROFILE.format(dm.profile.username))
                dm.story_share != null -> {
                    media = dm.story_share.media
                    if (dm.story_share.message == null)
                        b.msgIv.contentDescription = dm.story_share.message
                    b.msgTv.text = dm.story_share.text
                }
                dm.text != null -> b.msgTv.text = dm.text
            }
            b.msgIv.vis(media != null)
            media?.apply {
                if (carousel_media != null)
                    Glide.with(c).load(carousel_media[0].worst()).into(b.msgIv)
                else if (image_versions2 != null)
                    Glide.with(c).load(worst()).into(b.msgIv)
            }

            // Time
            b.time.text =
                "${z(cal[Calendar.HOUR_OF_DAY])}:${z(cal[Calendar.MINUTE])}:${z(cal[Calendar.SECOND])}"
        }
    }
}
