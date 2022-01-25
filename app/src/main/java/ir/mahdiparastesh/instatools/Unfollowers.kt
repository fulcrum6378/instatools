package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import ir.mahdiparastesh.instatools.data.Unfollower
import ir.mahdiparastesh.instatools.databinding.UnfollowersBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListUnf
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Delay

class Unfollowers(private val c: Main) : Fragment() {
    private lateinit var b: UnfollowersBinding
    private var following: List<Rest.User>? = null
    private var fetching = false

    companion object {
        var handler: Handler? = null
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = UnfollowersBinding.inflate(
            c.themeInflater(BaseActivity.Theme.PRIMARY, inf), parent, false
        )

        handler = object : Handler(Looper.getMainLooper()) {
            @Suppress("UNCHECKED_CAST")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    Action.LOADED.ordinal -> (msg.obj as List<Unfollower>).apply {
                        c.m.unfollowers = ArrayList(this)
                        if (isEmpty()) fetch() else adapt()
                    }
                    Action.FETCHED.ordinal -> {
                        following = msg.obj as List<Rest.User>
                        analyze()
                    }
                    Action.ANALYZED.ordinal -> fetching = false
                }
            }
        }

        when {
            Main.guest -> {
            }
            c.m.unfollowers != null -> adapt()
            else -> Thread {
                handler?.obtainMessage(Action.LOADED.ordinal, c.dao.unfollowers())?.sendToTarget()
            }.start()
        }
        return b.root
    }

    private fun fetch() {
        if (fetching) return
        fetching = true
        c.m.unfollowers = null
        c.dao.deleteUnfollowers()
        adapt()
        allFollow()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (b.rv.adapter == null) b.rv.adapter = ListUnf(c)
        else b.rv.adapter?.notifyDataSetChanged()
    }

    private fun allFollow(
        list: MutableList<Rest.User> = mutableListOf(), next_max_id: String = ""
    ) {
        Api<Rest.Follow>(
            c, Api.Type.FOLLOWING.url.format(c.m.id!!, next_max_id), Rest.Follow::class.java
        ) { flw ->
            list.addAll(flw.users.toMutableList())
            if (flw.next_max_id == null)
                handler?.obtainMessage(Action.FETCHED.ordinal, list)?.sendToTarget()
            else Delay { allFollow(list, flw.next_max_id) }
        }
    }

    private fun analyze(i: Int = 0) {
        if (c.m.unfollowers == null) return
        if (following == null || i >= following!!.size) {
            handler?.obtainMessage(Action.ANALYZED.ordinal)?.sendToTarget()
            return
        }
        Toast.makeText(c, "Analyzed: #${i + 1}", Toast.LENGTH_SHORT).show()
        Api<Profile>(
            c, Api.Type.PROFILE.url.format(following!![i].username), Profile::class.java
        ) { profile ->
            val u = profile.graphql.user
            if (u.follows_viewer == false) {
                val newbie = Unfollower(
                    u.id.toLong(), u.username, u.full_name,
                    u.profile_pic_url_hd ?: u.profile_pic_url,
                    u.edge_followed_by.count.toLong()
                )
                c.m.unfollowers!!.add(newbie)
                c.m.unfollowers!!.sortWith(Unfollower.Sort())
                c.dao.addUnfollower(newbie)
                Unfollower.find(newbie, c.m.unfollowers!!)
                    ?.let { b.rv.adapter?.notifyItemInserted(it) }
            }
            Delay(3000) { analyze(i + 1) }
        }
    }

    enum class Action { LOADED, FETCHED, ANALYZED }
}
