package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.databinding.PageSvdBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListSvd
import ir.mahdiparastesh.instatools.more.BaseActivity

class PageSvd(val c: Main) : Fragment() {
    private lateinit var b: PageSvdBinding

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageSvdBinding.inflate(
            c.themeInflater(BaseActivity.Theme.SECONDARY, inf), parent, false
        )

        when {
            Main.guest -> {
                // TODO: GUEST MODE
            }
            c.m.saved != null -> adapt()
            else -> fetchSome()
        }
        return b.root
    }

    private fun fetchSome() {
        if (c.m.nextSaved?.has_next_page == false) return
        if (c.m.saved == null) Api<Profile>(
            c, Api.Type.SAVED_FIRST.url.format(c.m.acc!!.user), Profile::class
        ) { profile ->
            val media = profile.graphql?.user?.edge_saved_media ?: return@Api
            c.m.nextSaved = media.page_info
            c.m.saved = ArrayList(media.edges.map { it.node })
            adapt()
            fetchSome()
        } else Api<Profile.GraphQlResponse>(
            c, Api.Type.SAVED.url.format(
                c.m.acc!!.id, c.m.saved!!.size, c.m.nextSaved?.end_cursor ?: ""
            ), Profile.GraphQlResponse::class
        ) { res ->
            val media = res.data.user?.edge_saved_media ?: return@Api
            c.m.nextSaved = media.page_info
            c.m.saved?.addAll(media.edges.map { it.node })
            adapt()
            fetchSome()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (b.rv.adapter == null) {
            b.rv.layoutManager = GridLayoutManager(c, 3)
            b.rv.adapter = ListSvd(c)
        } else b.rv.adapter?.notifyDataSetChanged()
    }
}
