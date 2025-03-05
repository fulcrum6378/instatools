package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
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
    private val suspendedAlpha = 0.7f

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListQudBinding> =
        AnyViewHolder(ListQudBinding.inflate(c.layoutInflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListQudBinding>, i: Int) {
        val q = c.c.downloads.getOrNull<Download>(i) ?: return

        // main
        if (q.type != 3.toByte()) Glide.with(c.c)
            .load(q.thumb)
            .diskCacheStrategy(DiskCacheStrategy.ALL) // uses caches by ListPost
            .signature(ObjectKey(q.id))
            .centerCrop()
            .into(h.b.thumb)
        else h.b.thumb.setImageResource(R.drawable.audio)
        h.b.user.text = "${i + 1}. ${q.owner}"
        h.b.date.text = Utils.date(q.date)

        // status
        syncStatus(h, q)
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
            CoroutineScope(Dispatchers.IO).launch {
                val q = c.c.downloads.getOrNull<Download>(h.layoutPosition) ?: return@launch
                when (q.status) {
                    0.toByte() -> q.status = 2
                    1.toByte() -> q.status = 0
                    2.toByte() -> q.status = 0
                }
                c.c.downloads.save<Download>()
                //Downloads.initService(c)
                withContext(Dispatchers.Main) {
                    syncStatus(h, q)
                }
            }
        }

        // separator
        h.b.sep.vis(i < itemCount - 1)
    }

    private fun syncStatus(h: AnyViewHolder<ListQudBinding>, q: Download) {
        when {
            q.status == 2.toByte() -> h.b.status.setImageResource(R.drawable.play)
            q == DownloadService.processingItem -> h.b.status.setAnimation(R.raw.download)
            q.isFailed() -> h.b.status.setAnimation(R.raw.failed)
            else -> h.b.status.setImageResource(R.drawable.pause)
        }
        (if (q.status == 2.toByte()) suspendedAlpha else 1f).also { alpha ->
            // don't set alpha on the root; 'cus RecyclerView will change it.
            h.b.thumb.alpha = alpha
            h.b.user.alpha = alpha
            h.b.date.alpha = alpha
        }
    }

    override fun getItemCount() = c.c.downloads.size<Download>()

    override fun onViewAttachedToWindow(h: AnyViewHolder<ListQudBinding>) {
        h.b.status.resumeAnimation()
    }

    override fun onViewDetachedFromWindow(h: AnyViewHolder<ListQudBinding>) {
        h.b.status.pauseAnimation()
    }
}
