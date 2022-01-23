package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ir.mahdiparastesh.instatools.data.Unfollower
import ir.mahdiparastesh.instatools.databinding.UnfollowersBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListUnf
import ir.mahdiparastesh.instatools.more.Delay

class Unfollowers(private val c: Main) : Fragment() {
    private lateinit var b: UnfollowersBinding
    private var following: List<Rest.User>? = null
    private var fetching = false
    private val myId = "8337021434"

    companion object {
        var handler: Handler? = null
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = UnfollowersBinding.inflate(layoutInflater, parent, false)

        handler = object : Handler(Looper.getMainLooper()) {
            @Suppress("UNCHECKED_CAST")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    0 -> {
                        following = msg.obj as List<Rest.User>
                        analyze()
                    }
                    1 -> {
                        fetching = false
                    }
                }
            }
        }

        fetch()
        return b.root
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetch() {
        if (fetching) return
        fetching = true
        c.m.unfollowers.clear()
        if (b.rv.adapter == null) b.rv.adapter = ListUnf(c)
        else b.rv.adapter?.notifyDataSetChanged()
        allFollow()
    }

    private fun allFollow(
        list: MutableList<Rest.User> = mutableListOf(), next_max_id: String = ""
    ) {
        Api<Rest.Follow>(
            c.c, Api.Type.FOLLOWING.url.format(myId, next_max_id), Rest.Follow::class.java
        ) { flw ->
            list.addAll(flw.users.toMutableList())
            if (flw.next_max_id == null)
                handler?.obtainMessage(0, list)?.sendToTarget()
            else Delay { allFollow(list, flw.next_max_id) }
        }
    }

    private fun analyze(i: Int = 0) {
        if (following == null || i >= following!!.size) {
            handler?.obtainMessage(1)?.sendToTarget()
            return
        }
        Api<Profile>(
            c.c, Api.Type.PROFILE.url.format(following!![i].username), Profile::class.java
        ) { profile ->
            val u = profile.graphql.user
            if (u.follows_viewer == false) {
                val newbie = Unfollower(
                    u.id.toLong(), u.username, u.full_name,
                    u.profile_pic_url_hd ?: u.profile_pic_url,
                    u.edge_followed_by.count.toLong()
                )
                c.m.unfollowers.add(newbie)
                c.m.unfollowers.sortWith(Unfollower.Sort())
                val where = c.m.unfollowers.indexOf(newbie)
                if (where > -1) b.rv.adapter?.notifyItemInserted(where)
            }
            //Delay { analyze(i + 1, list) }
            analyze(i + 1)
        }
    }
}
