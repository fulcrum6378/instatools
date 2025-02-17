package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModel
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.databinding.FriendsBinding
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.list.ListFri
import ir.mahdiparastesh.instatools.view.UserListActivity
import ir.mahdiparastesh.instatools.view.OnlineDataLoader
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Integer.min
import java.util.concurrent.CopyOnWriteArrayList

class Friends : UserListActivity(), OnlineDataLoader {
    private lateinit var b: FriendsBinding
    val mm: MyModel by viewModels()
    private val friendshipsDataLimit = 500 // no more no less

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val bRefresher: SwipeRefreshLayout get() = b.refresher
    override val bTbShadow: View get() = b.tbShadow

    companion object : ActivityCompanion()

    class MyModel : ViewModel() {
        val friends = CopyOnWriteArrayList<Friend>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FriendsBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.friends)

        prepareListing(this)
        error()?.setOnClickListener {
            b.refresher.isRefreshing = true
            load()
        }
        load()
    }

    override fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            mm.friends.clear()
            mm.friends.addAll(dao.friends())
            friendships(0)
        }
    }

    private suspend fun friendships(index: Int) {
        if (index >= mm.friends.size) {
            withContext(Dispatchers.Main) {
                onLoaded(mm.friends.isEmpty())
                adapt()
                updateCount(mm.friends.size)
            }
            return; }

        val rest = Api.call<Rest.Friendships>(
            Api.Endpoint.FRIENDSHIPS_MANY.url, Rest.Friendships::class,
            isPost = true, body = "user_ids=" + mm.friends
                .subList(index, min(index + friendshipsDataLimit, mm.friends.size))
                .joinToString(",") { it.id },
            onError = { code -> onFailed(Api.error(code)) }
        ) ?: return

        for (f in mm.friends) rest.friendship_statuses[f.id]?.also {
            f.bestie = it.is_bestie
            f.feedFav = it.is_feed_favorite == true
            f.restricted = it.is_restricted == true
        }
        friendships(index + friendshipsDataLimit)
    }

    override fun onLoaded(isEmpty: Boolean) {
        b.refresher.isRefreshing = false
        super.onLoaded(isEmpty)
    }

    override fun onFailed(message: String) {
        b.refresher.isRefreshing = false
        super.onFailed(message)
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
}
