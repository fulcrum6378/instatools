package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListBoxBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.xFromMicroseconds

class ListBox(val c: Main, private val f: PageBox) :
    RecyclerView.Adapter<AnyViewHolder<ListBoxBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListBoxBinding> =
        AnyViewHolder(ListBoxBinding.inflate(f.inflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListBoxBinding>, i: Int) {
        var thd = c.mm.dmInbox?.threads?.getOrNull(i) ?: return
        val firstUser = thd.users.getOrNull(0)
        if (firstUser == null && !thd.is_group) return
        if (!thd.is_group) Glide.with(c.c).load(firstUser!!.profile_pic_url).into(h.b.photo)
        else h.b.photo.setImageResource(R.drawable.switch_account)
        h.b.name.text = thd.title()

        h.b.last.text =
            c.getString(R.string.boxUntil, UiTools.date(thd.last_activity_at.xFromMicroseconds()))
        h.b.root.setOnClickListener {
            c.mm.dmThread =
                c.mm.dmInbox?.threads?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            c.mm.dmThread!!.items.sortBy { it.timestamp }
            f.onLoaded(false)
            f.fetchOfThread()
        }
        h.b.more.setOnClickListener {
            thd = c.mm.dmInbox?.threads?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            MaterialMenu(
                c, it, R.menu.box_more, Act().apply {
                    this[R.id.bmHtml] = { f.expOptions(Exporter.Method.HTML, thd) }
                    this[R.id.bmPdf] = { f.expOptions(Exporter.Method.PDF, thd) }
                    this[R.id.bmTxt] = { f.expOptions(Exporter.Method.TXT, thd) }
                    this[R.id.bmOpenDmInInsta] = { UiTools.openDm(c, thd.thread_id) }
                    this[R.id.bmMarkAsSeen] = {
                        val last = thd.items.lastOrNull()
                        if (last != null) f.reqQueue.adder = Api<Rest.Seen>(
                            c, Api.Endpoint.SEEN.url.format(thd.thread_id, last.item_id),
                            Rest.Seen::class, null, method = Request.Method.POST,
                            autoQueue = false /*, onError = {}*/
                        ) { rest -> if (rest.status_code == "200") thd.read_state = 0.0 }
                    }
                    this[R.id.bmView] = {
                        thd.users.getOrNull(0)?.let { uu -> Viewer.comeHere(c, uu.username) }
                    }
                }, R.style.Theme_InstaTools_Popup_Tertiary
            ).apply {
                if (thd.is_group || thd.users.getOrNull(0)?.full_name == "Instagram user")
                    menu.findItem(R.id.bmView)?.let { i -> i.isVisible = false }
                if (thd.read_state != 1.0)
                    menu.findItem(R.id.bmMarkAsSeen)?.let { i -> i.isVisible = false }
            }.show()
        }
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.mm.dmInbox?.threads?.size ?: 0

    override fun onViewAttachedToWindow(h: AnyViewHolder<ListBoxBinding>) {
        super.onViewAttachedToWindow(h)
        h.b.more.setImageResource(R.drawable.more_vert)
    }
}
