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
        val u = c.m.dmThreads!![i].users[0]
        Glide.with(c.c).load(u.profile_pic_url).into(h.b.photo)
        h.b.name.text = if (u.full_name != "") u.full_name else u.username
        h.b.last.text = c.getString(
            R.string.boxUntil, UiTools.date(c.m.dmThreads!![i].last_activity_at)
        )
        h.b.root.setOnClickListener {
            c.m.dmThread = c.m.dmThreads?.get(h.layoutPosition)
            f.adapt()
            if (c.m.dmThread == null || !c.m.dmThread!!.has_older) return@setOnClickListener
            f.thdThread = PageBox.FetchSomeDm(
                c, c.m.dmThread!!.thread_id, c.m.dmThread!!.items.first().item_id, f.handler
            ).also { it.start() }
        }
        h.b.more.setOnClickListener {
            val thd = c.m.dmThreads?.get(h.layoutPosition) ?: return@setOnClickListener
            MaterialMenu(c, it, R.menu.box_more, Act().apply {
                this[R.id.bmPdf] = {
                    f.expOptions(Exporter.Method.PDF, u.username, thd)
                }
                this[R.id.bmView] = {
                    thd.users.getOrNull(0)?.let { uu -> Viewer.comeHere(c, uu.pk, uu.username) }
                }
            }, c.colorAc.value).show()
        }
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.m.dmThreads?.size ?: 0
}
