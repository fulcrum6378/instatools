package ir.mahdiparastesh.instatools.list

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListThdBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.api.Dm
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.more.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.job.Exporter
import ir.mahdiparastesh.instatools.job.Exporter.Downloadable
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.FastCustomGlide
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.PROFILE
import ir.mahdiparastesh.instatools.view.UiTools.anchor
import ir.mahdiparastesh.instatools.more.Utils.calendar
import ir.mahdiparastesh.instatools.more.Utils.getOrNull
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.more.Utils.xFromMicroseconds
import ir.mahdiparastesh.instatools.more.Utils.z
import java.io.FileInputStream
import java.util.*

class ListThd(val c: Main, private val f: PageBox) :
    RecyclerView.Adapter<AnyViewHolder<ListThdBinding>>() {
    private val idealW = (c.dm.widthPixels.toFloat() * 0.8f) * c.dm.density

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListThdBinding> = AnyViewHolder(
        ListThdBinding.inflate(f.inflater, parent, false).onCreate()
    )

    override fun onBindViewHolder(h: AnyViewHolder<ListThdBinding>, i: Int) {
        if (c.mm.dmThread != null) h.b.onBind(c.c, c.mm.dmThread!!, i, idealW, f, h)
    }

    override fun getItemCount() = c.mm.dmThread?.items?.size ?: 0

    companion object {
        fun ListThdBinding.onCreate(isExporting: Boolean = false): ListThdBinding {
            if (isExporting) msgIvCl.removeView(msgLoading)
            return this
        }

        @SuppressLint("CheckResult", "SetTextI18n")
        fun ListThdBinding.onBind(
            c: Context, thread: Dm.DmThread, i: Int, idealW: Float = Media.Version.BEST,
            f: PageBox? = null, h: AnyViewHolder<ListThdBinding>? = null,
            downloaded: HashMap<String, Downloadable>? = null,
        ): ListThdBinding {
            val dm = thread.items.getOrNull(i) ?: return this
            body.vis(dm.action_log == null)

            // Date
            val cal = dm.timestamp.xFromMicroseconds().calendar()
            var showDate = true
            if (i > 0) {
                val prev = thread.items[i - 1].timestamp.xFromMicroseconds().calendar()
                if (cal[Calendar.YEAR] == prev[Calendar.YEAR] &&
                    cal[Calendar.MONTH] == prev[Calendar.MONTH] &&
                    cal[Calendar.DAY_OF_MONTH] == prev[Calendar.DAY_OF_MONTH]
                ) showDate = false
            }
            date.vis(showDate)
            if (showDate) date.text =
                "${cal[Calendar.YEAR]}.${z(cal[Calendar.MONTH] + 1)}.${z(cal[Calendar.DAY_OF_MONTH])}"

            if (dm.action_log != null) return this

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
                if (dm.is_sent_by_viewer) AppCompatTextView.TEXT_ALIGNMENT_VIEW_END
                else AppCompatTextView.TEXT_ALIGNMENT_VIEW_START

            // Profile
            val next = thread.items.filter { it.action_log == null }
                .find { it.item_id == thread.items.getOrNull(i + 1)?.item_id }
            val showPro = !dm.is_sent_by_viewer && dm.user_id != next?.user_id
            profile.visibility = when {
                dm.is_sent_by_viewer -> View.GONE
                showPro -> View.VISIBLE
                else -> View.INVISIBLE
            }
            val userId = dm.user_id.toLong().toString()
            if (downloaded == null) {
                if (showPro) Glide.with(c).asBitmap()
                    .load(thread.users.find { it.pk == userId }?.profile_pic_url)
                    .into(UiTools.targetProfile(profile))
                else Glide.with(c).clear(profile)
            } else downloaded.getOrNull(Exporter.USER_PROFILE_IMG.format(userId))
                ?.cache?.also {
                    FileInputStream(it).use { fis ->
                        val data = fis.readBytes()
                        if (data.isNotEmpty()) profile.setImageBitmap(
                            UiTools.bmpRound(BitmapFactory.decodeByteArray(data, 0, data.size))
                        )
                    }
                }

            // Message
            msgTv.anchor(null, null)
            msgIvHint.vis(false)
            var media: Media? = null
            when {
                dm.animated_media != null ->
                    msgIvHint.apply { text = "Sent a sticker"; vis() }
                dm.clip != null -> media = dm.clip.clip
                dm.direct_media_share != null -> {
                    media = dm.direct_media_share.media
                    msgTv.text = dm.direct_media_share.text
                }
                dm.felix_share != null -> {
                    media = dm.felix_share.video
                    if (dm.felix_share.message != null)
                        msgIvHint.apply { text = dm.felix_share.message; vis() }
                    msgTv.text = dm.felix_share.text
                }
                dm.like != null -> msgTv.text = dm.like
                dm.link != null -> msgTv.anchor(dm.link.text, dm.link.link_context.link_url)
                dm.live_viewer_invite != null -> {
                    msgIvHint.apply { text = dm.live_viewer_invite.cta_button_name; vis() }
                    msgTv.text = dm.live_viewer_invite.text
                }
                dm.media != null -> media = dm.media
                dm.media_share != null -> media = dm.media_share
                dm.placeholder != null -> msgIvHint.apply { text = dm.placeholder.message; vis() }
                dm.profile != null -> msgTv.anchor(
                    "@${dm.profile.username} [User ID: ${dm.profile.pk}]",
                    PROFILE.format(dm.profile.username)
                )
                dm.raven_media != null -> media = dm.raven_media
                dm.reel_share != null -> {
                    media = dm.reel_share.media
                    if (dm.reel_share.message != null)
                        msgIvHint.apply { text = dm.reel_share.message; vis() }
                    msgTv.text = dm.reel_share.text
                }
                dm.story_share != null -> {
                    media = dm.story_share.media
                    if (dm.story_share.message != null)
                        msgIvHint.apply { text = dm.story_share.message; vis() }
                    msgTv.text = dm.story_share.text
                }
                dm.text != null -> msgTv.text = dm.text
                dm.video_call_event != null ->
                    msgIvHint.apply { text = dm.video_call_event.description; vis() }
                dm.voice_media != null ->
                    msgIvHint.apply { text = "Voice message omitted!"; vis() }
                else ->
                    if (BuildConfig.DEBUG) throw Exception(
                        "NEW DM TYPE \"${dm.item_type}\" with id: ${dm.item_id}"
                    )
                    else msgIvHint.apply { text = "Unknown DM type \"${dm.item_type}\"!!"; vis() }
            }
            msgIvCl.vis(media != null)
            Glide.with(c).clear(msgIv)
            msgIv.setImageDrawable(null)
            media?.apply {
                downloaded?.getOrNull(dm.item_id)?.cache?.also {
                    FileInputStream(it).use { fis ->
                        val data = fis.readBytes()
                        if (data.isNotEmpty()) {
                            msgIv.setImageBitmap(BitmapFactory.decodeByteArray(data, 0, data.size))
                            return@apply; }
                    }
                }
                if (downloaded != null) return@apply

                msgLoading.apply {
                    setAnimation(if (!c.night()) R.raw.pending_tertiary else R.raw.pending)
                    playAnimation()
                    vis()
                }
                Glide.with(c).load(
                    carousel_media?.getOrNull(0)?.nearest(idealW) ?: nearest(idealW)
                ).diskCacheStrategy(DiskCacheStrategy.RESOURCE).into(
                    FastCustomGlide {
                        if (h != null && h.layoutPosition != i) return@FastCustomGlide
                        msgLoading.pauseAnimation()
                        msgLoading.vis(false)
                        msgIv.layoutParams = msgIv.layoutParams.apply {
                            width = WRAP_CONTENT
                            height = WRAP_CONTENT
                        }
                        msgIv.setImageDrawable(it)
                    })
            }
            if (downloaded == null) mediaClick.setOnClickListener {
                if (f?.expandable == null || media !is Media) return@setOnClickListener
                f.expandable.media = media
                f.expandable.thumb = root
                try {
                    f.expandable.expand()
                    f.jumper()?.vis(false)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) throw e
                }
            }
            msgTv.vis(msgTv.text.isNotBlank())

            // Reactions
            reactions.removeAllViews()
            reactions.vis(dm.reactions != null)
            if (dm.reactions != null) for (r in dm.reactions.emojis) reactions.addView(
                AppCompatTextView(c).apply {
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
