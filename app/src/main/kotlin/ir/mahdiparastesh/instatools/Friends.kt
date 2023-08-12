package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.databinding.FriendsBinding
import ir.mahdiparastesh.instatools.list.ListFri
import ir.mahdiparastesh.instatools.more.UserListActivity
import ir.mahdiparastesh.instatools.view.UiTools.vis
import java.util.concurrent.CopyOnWriteArrayList

class Friends : UserListActivity() {
    private lateinit var b: FriendsBinding
    val mm: MyModel by viewModels()

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion
    override val bRefresher: SwipeRefreshLayout get() = b.refresher
    override val bRv: RecyclerView get() = b.rv
    override val bTbShadow: View get() = b.tbShadow
    override val bJumper: ImageView get() = b.jumper

    companion object : ActivityCompanion()

    class MyModel : ViewModel() {
        val friends = CopyOnWriteArrayList<Friend>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FriendsBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.friends)

        handler = object : Handler(Looper.getMainLooper()) {
            @SuppressLint("UnsafeOptInUsageError")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_LOADED -> {
                        b.refresher.isRefreshing = false
                        adapt()
                        updateCount(mm.friends.size)
                    }
                }
            }
        }

        prepare()
        load()
    }

    override fun load() {
        FriLoader().start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (mm.friends.isNotEmpty()) {
            if (b.rv.adapter == null) b.rv.adapter = ListFri(this)
            else b.rv.adapter?.notifyDataSetChanged()
        } else {
            b.rv.vis(false)
            b.empty.vis(true)
        }
    }

    inner class FriLoader : Thread() {
        override fun run() {
            mm.friends.clear()
            mm.friends.addAll(dao.friends())
            //mm.friends.sortBy { it.user }
            handler?.obtainMessage(HANDLE_LOADED)?.sendToTarget()
        }
    }
}
