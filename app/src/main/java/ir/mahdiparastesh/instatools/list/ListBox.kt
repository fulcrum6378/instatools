package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListBoxBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.xFromMicroseconds

class ListBox(val c: Main, private val f: PageBox) : RecyclerView.Adapter<ListBox.ViewHolder>() {
    class ViewHolder(val b: ListBoxBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListBoxBinding.inflate(f.inflater, parent, false)
        b.name.typeface = c.fontRegular
        b.last.typeface = c.fontRegular
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        var thd = c.m.dmInbox?.threads?.getOrNull(i) ?: return
        val firstUser = thd.users.getOrNull(0)
        if (firstUser == null && !thd.is_group) return
        // thd.users MAY HAVE BEEN EMPTY AND CAUSED THOSE 34-TIME CRASHES
        if (!thd.is_group) {
            Glide.with(c.c).load(firstUser!!.profile_pic_url).into(h.b.photo)
            h.b.name.text = firstUser.visName()
        } else {
            h.b.photo.setImageResource(R.drawable.switch_account)
            h.b.name.text = thd.thread_title
        }

        h.b.last.text =
            c.getString(R.string.boxUntil, UiTools.date(thd.last_activity_at.xFromMicroseconds()))
        h.b.root.setOnClickListener {
            c.m.dmThread =
                c.m.dmInbox?.threads?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            f.onLoaded(false)
            f.thdThread = PageBox.FetchOfThread(
                c, c.m.dmThread!!.thread_id, c.m.dmThread!!.items.firstOrNull()?.item_id ?: "",
                PageBox.handler
            ).also { it.start() }
        }
        h.b.more.setOnClickListener {
            thd = c.m.dmInbox?.threads?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            MaterialMenu(
                c.wrapTheme(BaseActivity.Theme.TERTIARY), c.fontRegular, it, R.menu.box_more,
                Act().apply {
                    this[R.id.bmHtml] = {
                        thd.users.getOrNull(0)
                            ?.let { uu -> f.expOptions(Exporter.Method.HTML, uu.username, thd) }
                    }
                    this[R.id.bmPdf] = {
                        thd.users.getOrNull(0)
                            ?.let { uu -> f.expOptions(Exporter.Method.PDF, uu.username, thd) }
                    }
                    this[R.id.bmTxt] = {
                        thd.users.getOrNull(0)
                            ?.let { uu -> f.expOptions(Exporter.Method.TXT, uu.username, thd) }
                    }
                    this[R.id.bmOpenDmInInsta] = {
                        UiTools.openDm(c, thd.thread_id)
                    }
                    this[R.id.bmView] = {
                        thd.users.getOrNull(0)?.let { uu -> Viewer.comeHere(c, uu.username) }
                    }
                }, c.colorAc.value
            ).apply {
                if (thd.is_group || thd.users.getOrNull(0)?.full_name == "Instagram user")
                    menu.findItem(R.id.bmView)?.let { i -> i.isVisible = false }
                if (!BuildConfig.DEBUG) menu.findItem(R.id.bmHtml).isVisible = false
            }.show()
        }
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.m.dmInbox?.threads?.size ?: 0

    override fun onViewAttachedToWindow(h: ViewHolder) {
        super.onViewAttachedToWindow(h)
        h.b.more.setImageResource(R.drawable.more_vert)
    }
}
