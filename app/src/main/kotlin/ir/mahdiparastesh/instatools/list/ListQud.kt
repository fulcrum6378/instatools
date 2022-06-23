package ir.mahdiparastesh.instatools.list

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
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

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(h: AnyViewHolder<ListQudBinding>, i: Int) {
        val qud = c.m.queueds?.getOrNull(i) ?: return

        // Main
        Glide.with(c.c).load(qud.thumb).into(h.b.thumb)
        h.b.user.text = "${i + 1}. ${qud.userName ?: "..."}"
        h.b.date.text = UiTools.date(qud.addedAt)

        // Status
        h.b.status.setAnimation(
            when {
                qud.failed -> R.raw.failed
                i > 0 || !Queuer.active.value!! -> R.raw.pending
                else -> R.raw.download
            }
        )
        h.b.status.repeatCount = if (qud.failed) 0 else LottieDrawable.INFINITE
        val pad = if (!qud.failed) c.resources.getDimension(R.dimen.qudLoadingPad).toInt() else 0
        h.b.status.setPadding(pad, pad, pad, pad)
        h.b.status.isClickable = qud.failed

        // Clicks
        h.b.root.setOnClickListener {
            c.m.queueds?.getOrNull(h.layoutPosition)?.let {
                if (it.link.isBlank()) return@let
                try {
                    c.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(it.link))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: ActivityNotFoundException) {
                }
            }
        }
        h.b.status.isClickable = qud.failed
        h.b.status.setOnClickListener(if (qud.failed) View.OnClickListener {
            c.m.queueds?.getOrNull(h.layoutPosition)?.apply {
                CoroutineScope(Dispatchers.IO).launch {
                    failed = false
                    c.dao.updateQueued(this@apply)
                    withContext(Dispatchers.Main) {
                        c.b.rv.adapter?.notifyItemChanged(h.layoutPosition)
                        Downloads.initService(c, "")
                    }
                }
            }
        } else null)

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
