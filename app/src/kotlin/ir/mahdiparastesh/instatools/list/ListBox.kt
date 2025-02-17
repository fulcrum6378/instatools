package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.databinding.ListBoxBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.job.Exporter
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.xFromMicroseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            MaterialMenu(c, it, R.menu.box_more,
                R.id.bmHtml to { f.expOptions(Exporter.Method.HTML, thd) },
                R.id.bmPdf to { f.expOptions(Exporter.Method.PDF, thd) },
                R.id.bmTxt to { f.expOptions(Exporter.Method.TXT, thd) },
                R.id.bmOpenDmInInsta to { UiTools.openDm(c, thd.thread_id) },
                R.id.bmMarkAsSeen to {
                    val last = thd.items.lastOrNull()
                    if (last != null) CoroutineScope(Dispatchers.IO).launch {
                        val rest = Api.call<Rest.Seen>(
                            Api.Endpoint.SEEN.url.format(thd.thread_id, last.item_id),
                            Rest.Seen::class, isPost = true
                        )
                        if (rest?.status_code == "200") thd.read_state = 0.0
                    }
                },
                R.id.bmView to {
                    thd.users.getOrNull(0)?.let { uu -> Viewer.comeHere(c, uu.username!!) }
                }, theme = R.style.Theme_InstaTools_Popup_Tertiary
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
