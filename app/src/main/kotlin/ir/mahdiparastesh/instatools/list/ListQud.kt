package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieDrawable
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListQudBinding
import ir.mahdiparastesh.instatools.serv.Queuer
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListQud(val c: Downloads) : RecyclerView.Adapter<AnyViewHolder<ListQudBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListQudBinding> =
        AnyViewHolder(ListQudBinding.inflate(c.layoutInflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListQudBinding>, i: Int) {
        val qud = c.m.queueds?.getOrNull(i) ?: return

        // Main
        if (qud.mediaType != 3.toByte()) Glide.with(c.c).load(qud.thumb).into(h.b.thumb)
        else h.b.thumb.setImageResource(R.drawable.audio)
        h.b.user.text = "${i + 1}. ${qud.userName ?: "..."}"
        h.b.date.text = UiTools.date(qud.addedAt)

        // Status
        if (qud.status == 2.toByte())
            h.b.status.setImageResource(R.drawable.play)
        else h.b.status.setAnimation(
            when {
                qud.isFailed() -> R.raw.failed
                i > 0 || !Queuer.active.value!! -> R.raw.pending
                else -> R.raw.download
            }
        )
        h.b.status.repeatCount = if (qud.isFailed()) 0 else LottieDrawable.INFINITE
        val pad =
            if (!qud.isFailed()) c.resources.getDimension(R.dimen.qudLoadingPad).toInt() else 0
        h.b.status.setPadding(pad, pad, pad, pad)

        // Clicks
        h.b.root.setOnClickListener {
            c.m.queueds?.getOrNull(h.layoutPosition)?.let {
                if (it.link.isNotBlank()) UiTools.openLink(c, it.link)
            }
        }
        h.b.status.setOnClickListener {
            if (h.layoutPosition == 0) return@setOnClickListener
            c.m.queueds?.getOrNull(h.layoutPosition)?.apply {
                CoroutineScope(Dispatchers.IO).launch {
                    when (status) {
                        0.toByte() -> status = 2.toByte()
                        1.toByte() -> status = 0.toByte()
                        2.toByte() -> status = 0.toByte()
                    }
                    c.dao.updateQueued(this@apply)
                    withContext(Dispatchers.Main) {
                        c.b.rv.adapter?.notifyItemChanged(h.layoutPosition)
                        Downloads.initService(c, "")
                    }
                }
            }
        }

        // Separator
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.m.queueds?.size ?: 0

    override fun onViewAttachedToWindow(h: AnyViewHolder<ListQudBinding>) {
        h.b.status.resumeAnimation()
    }

    override fun onViewDetachedFromWindow(h: AnyViewHolder<ListQudBinding>) {
        h.b.status.pauseAnimation()
    }
}
