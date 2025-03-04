package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieDrawable
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.databinding.ListQudBinding
import ir.mahdiparastesh.instatools.job.DownloadService
import ir.mahdiparastesh.instatools.util.Utils
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
        val q = c.c.downloads.getOrNull<Download>(i) ?: return

        // main
        if (q.type != 3.toByte()) Glide.with(c.c).load(q.thumb).into(h.b.thumb)
        else h.b.thumb.setImageResource(R.drawable.audio)
        h.b.user.text = "${i + 1}. ${q.owner}"
        h.b.date.text = Utils.date(q.date)

        // status
        when {
            q.status == 2.toByte() -> h.b.status.setImageResource(R.drawable.play)
            q == DownloadService.processingItem -> h.b.status.setAnimation(R.raw.download)
            q.isFailed() -> h.b.status.setAnimation(R.raw.failed)
            else -> h.b.status.setImageDrawable(null)
        }
        h.b.status.repeatCount = if (q.isFailed()) 0 else LottieDrawable.INFINITE
        val pad =
            if (!q.isFailed()) c.resources.getDimension(R.dimen.qudLoadingPad).toInt() else 0
        h.b.status.setPadding(pad, pad, pad, pad)

        // clicks
        h.b.root.setOnClickListener {
            c.c.downloads.getOrNull<Download>(h.layoutPosition)?.also {
                if (it.link.isNotEmpty()) UiTools.openLink(c, it.link)
            }
        }
        h.b.status.setOnClickListener {
            c.c.downloads.getOrNull<Download>(h.layoutPosition)?.apply {
                if (h.layoutPosition == 0 && status == 0.toByte()) return@setOnClickListener
                CoroutineScope(Dispatchers.IO).launch {
                    when (status) {
                        0.toByte() -> status = 2
                        1.toByte() -> status = 0
                        2.toByte() -> status = 0
                    }
                    c.c.downloads.save<Download>()
                    Downloads.initService(c)
                    withContext(Dispatchers.Main) {
                        c.b.rv.adapter?.notifyItemChanged(h.layoutPosition)
                    }
                }
            }
        }

        // separator
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.c.downloads.size<Download>()

    override fun onViewAttachedToWindow(h: AnyViewHolder<ListQudBinding>) {
        h.b.status.resumeAnimation()
    }

    override fun onViewDetachedFromWindow(h: AnyViewHolder<ListQudBinding>) {
        h.b.status.pauseAnimation()
    }
}
