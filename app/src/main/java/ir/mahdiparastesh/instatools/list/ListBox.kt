package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListBoxBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

class ListBox(val c: Main, private val f: PageBox) : RecyclerView.Adapter<ListBox.ViewHolder>() {
    class ViewHolder(val b: ListBoxBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListBoxBinding.inflate(f.inflater, parent, false)
        b.name.typeface = c.fontRegular
        b.last.typeface = c.fontRegular
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.dmThreads == null) return
        var thd = c.m.dmThreads!![i]
        if (!thd.is_group) {
            Glide.with(c.c).load(thd.users[0].profile_pic_url).into(h.b.photo)
            h.b.name.text = thd.users[0].visName()
        } else {
            h.b.photo.setImageResource(R.drawable.switch_account)
            h.b.name.text = thd.thread_title
        }

        h.b.last.text = c.getString(R.string.boxUntil, UiTools.date(thd.last_activity_at))
        h.b.root.setOnClickListener {
            c.m.dmThread = c.m.dmThreads?.get(h.layoutPosition)
            f.onLoaded()
            if (c.m.dmThread == null || !c.m.dmThread!!.has_older) return@setOnClickListener
            f.thdThread = PageBox.FetchSomeDm(
                c, c.m.dmThread!!.thread_id, c.m.dmThread!!.items.first().item_id, f.handler
            ).also { it.start() }
        }
        h.b.more.setOnClickListener {
            thd = c.m.dmThreads?.get(h.layoutPosition) ?: return@setOnClickListener
            MaterialMenu(c, it, R.menu.box_more, Act().apply {
                this[R.id.bmPdf] = {
                    thd.users.getOrNull(0)
                        ?.let { uu -> f.expOptions(Exporter.Method.PDF, uu.username, thd) }
                }
                this[R.id.bmView] = {
                    thd.users.getOrNull(0)?.let { uu -> Viewer.comeHere(c, uu.pk, uu.username) }
                }
            }, c.colorAc.value).apply {
                if (thd.is_group) menu.findItem(R.id.bmView)?.let { i -> i.isVisible = false }
            }.show()
        }
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.m.dmThreads?.size ?: 0
}
