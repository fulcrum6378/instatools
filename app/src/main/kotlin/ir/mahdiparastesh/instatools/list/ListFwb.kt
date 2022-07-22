package ir.mahdiparastesh.instatools.list

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.MassFollower
import ir.mahdiparastesh.instatools.databinding.ListFwbBinding
import ir.mahdiparastesh.instatools.more.ServiceOwnerActivity
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.themeColor
import kotlinx.coroutines.runBlocking

class ListFwb(val c: MassFollower) : RecyclerView.Adapter<AnyViewHolder<ListFwbBinding>>() {
    private val bg = c.themeColor(android.R.attr.windowBackground)
    private val ca = c.themeColor(android.R.attr.colorAccent)
    private val bgCf = PorterDuffColorFilter(bg, PorterDuff.Mode.SRC_IN)

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListFwbBinding> {
        val b = ListFwbBinding.inflate(c.layoutInflater, parent, false)
        b.root.chipBackgroundColor = ColorStateList.valueOf(ca)
        b.root.setTextColor(bg)
        b.root.closeIcon?.apply { colorFilter = bgCf }
        return AnyViewHolder(b)
    }

    override fun onBindViewHolder(h: AnyViewHolder<ListFwbBinding>, i: Int) {
        val fwb = c.mm.fwb.value?.getOrNull(i) ?: return
        h.b.root.text = fwb.user
        h.b.root.setOnClickListener { UiTools.openProfile(c, fwb.user) }
        h.b.root.setOnCloseIconClickListener {
            Thread {
                runBlocking { c.dao.deleteFollowable(fwb) }
                MassFollower.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_DELETED, fwb)
                    ?.sendToTarget()
            }.start()
        }
    }

    override fun getItemCount() = c.mm.fwb.value?.size ?: 0
}
