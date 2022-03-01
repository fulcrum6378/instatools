package ir.mahdiparastesh.instatools.list

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.TypedValue
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.MassFollower
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListFwbBinding
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.runBlocking

class ListFwb(val c: MassFollower) : RecyclerView.Adapter<ListFwb.ViewHolder>() {
    private val bg = TypedValue().apply {
        c.theme.resolveAttribute(R.attr.backgroundColor, this, true)
    }.data
    private val ca = TypedValue().apply {
        c.theme.resolveAttribute(R.attr.colorAccent, this, true)
    }.data
    private val bgCf = PorterDuffColorFilter(bg, PorterDuff.Mode.SRC_IN)

    class ViewHolder(val b: ListFwbBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListFwbBinding.inflate(c.layoutInflater, parent, false)
        b.root.typeface = c.fontRegular
        b.root.chipBackgroundColor = ColorStateList.valueOf(ca)
        b.root.setTextColor(bg)
        b.root.closeIcon?.apply { colorFilter = bgCf }
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        val fwb = c.m.fwb.value?.getOrNull(i) ?: return
        h.b.root.text = fwb.user
        h.b.root.setOnClickListener {
            UiTools.openProfile(c, fwb.user)
        }
        h.b.root.setOnCloseIconClickListener {
            Thread {
                runBlocking { c.dao.deleteFollowable(fwb) }
                MassFollower.handler?.obtainMessage(MassFollower.HANDLE_DELETED, fwb)
                    ?.sendToTarget()
            }.start()
        }
    }

    override fun getItemCount() = c.m.fwb.value?.size ?: 0
}
