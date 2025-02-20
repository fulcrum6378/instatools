package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.databinding.FriendsBinding
import ir.mahdiparastesh.instatools.list.ListFri
import ir.mahdiparastesh.instatools.view.CounterActivity
import ir.mahdiparastesh.instatools.view.OnlineLister
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Integer.min
import java.util.concurrent.CopyOnWriteArrayList

class Friends : CounterActivity(), OnlineLister {
    private lateinit var b: FriendsBinding
    val mm: MyModel by viewModels()
    private val friendshipsDataLimit = 500 // no more no less

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null
    override val bInitialised: Boolean get() = ::b.isInitialized
    override var shouldShowJumper = MutableLiveData(false)
    override var anJumper: ObjectAnimator? = null
    override val heightPixels: Int by lazy { dm.heightPixels }

    companion object : ActivityCompanion()

    class MyModel : ViewModel() {
        val friends = CopyOnWriteArrayList<Friend>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FriendsBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.friends)

        if (!Main.guest) {
            prepareListing(this)
            b.refresher.setOnRefreshListener { load() }
        } else {
            b.refresher.isEnabled = false
            jumper?.vis(false)
            rv?.vis(false)
        }
        error?.setOnClickListener {
            b.refresher.isRefreshing = true
            load()
        }
        load()
    }

    fun load() {
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
            onError = { code -> onFailed(code) }
        ) ?: return

        for (f in mm.friends) rest.friendship_statuses[f.id]?.also {
            f.bestie = it.is_bestie
            f.feedFav = it.is_feed_favorite == true
            f.restricted = it.is_restricted == true
        }
        friendships(index + friendshipsDataLimit)
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

    override fun updateShadow() {
        if (::b.isInitialized) b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
    }
}
