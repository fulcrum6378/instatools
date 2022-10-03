package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabaseLockedException
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.android.volley.NetworkResponse
import com.android.volley.toolbox.Volley
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.data.Friend.Companion.specialSort
import ir.mahdiparastesh.instatools.databinding.PageUnfBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListUnf
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.*

class PageUnf : BasePageMain() {
    lateinit var b: PageUnfBinding
    var thread: Inquiry? = null
    val reqQueue by lazy { Volley.newRequestQueue(c) }

    override val com: PageCompanion = Companion
    override val theme: BaseActivity.Theme = BaseActivity.Theme.PRIMARY
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null
    override val emptyIcon: Int = R.drawable.done_unf
    override val selectiveMenuRes: Int? = null

    @Suppress("UNCHECKED_CAST")
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        HANDLE_LOADED to { msg ->
            (msg.obj as List<Friend>).apply {
                c.mm.unfollowers.value = ArrayList(this).apply {
                    val favIds = (c.m.fav ?: listOf()).map { it.id }
                    for (f in 0 until size) this[f].inFav = this[f].id in favIds
                    specialSort()
                }
                if (isNullOrEmpty() && msg.arg1 == 1 &&
                    (Persistent.now() - (c.sp?.getLong(Settings.spUnfLastChecked, 0L)
                        ?: 0L)) > 86400000
                ) thread = Inquiry(c).also { it.start() }
                else onLoaded(isNullOrEmpty())
            }
        },
        HANDLE_FETCHED to {
            load(false)
            b.refresher.isRefreshing = false
            c.sp?.edit { putLong(Settings.spUnfLastChecked, Persistent.now()) }
        },
        Api.HANDLE_ERROR to {
            onFailed(
                c.getString(
                    R.string.unknownError, (it.obj as NetworkResponse?)?.statusCode.toString()
                )
            )
        },
        HANDLE_COULD_NOT to {
            UiTools.snackbar(b.root, R.string.unfCouldNot, Snackbar.LENGTH_SHORT, c.b.bnv)
        },
        HANDLE_FAV_CHANGED to { msg ->
            var id: String? = null
            var favNow = false
            if (msg.obj is Favourite) (msg.obj as Favourite).also {
                c.m.fav?.add(it)
                id = it.id
                favNow = true
            }
            if (msg.obj is String) (msg.obj as String).also {
                c.m.fav?.removeAll { f -> f.id == it }
                id = it
            }
            Friend.find(id!!, c.mm.unfollowers.value)?.also { before ->
                c.mm.unfollowers.value?.getOrNull(before)?.inFav = favNow
                c.mm.unfollowers.value?.specialSort()
                Friend.find(id!!, c.mm.unfollowers.value)?.also { after ->
                    b.rv.adapter?.notifyItemMoved(before, after)
                    when {
                        before > after -> b.rv.adapter
                            ?.notifyItemRangeChanged(after, (before - after) + 1)
                        after > before -> b.rv.adapter
                            ?.notifyItemRangeChanged(before, (after - before) + 1)
                        else -> b.rv.adapter?.notifyItemChanged(after)
                    }
                }
            }
        }
    )

    companion object : PageCompanion() {
        const val HANDLE_LOADED = 2
        const val HANDLE_COULD_NOT = 3
        const val HANDLE_FAV_CHANGED = 4
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageUnfBinding.inflate(inflater, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Main.guest) return

        if (c.mm.unfollowers.value != null) onLoaded(c.mm.unfollowers.value.isNullOrEmpty())
        else load(true)
    }

    override fun onResume() {
        super.onResume()
        (c.c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(Notify.ID_UNF_NEW_ITEMS)
    }

    override fun onRefresh() {
        if (thread?.active == true) return
        b.rv.adapter = null
        thread = Inquiry(c).also { it.start() }
    }

    private fun load(initial: Boolean) {
        Thread {
            handler?.obtainMessage(HANDLE_LOADED, if (initial) 1 else 0, 0, c.dao.unfollowers())
                ?.sendToTarget()
        }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        super.onLoaded(isEmpty, asGuest)
        if (b.rv.adapter == null) b.rv.adapter = ListUnf(c, this)
        else b.rv.adapter?.notifyDataSetChanged()
    }


    class Inquiry(c: Persistent) : DbRelatedThread(c) {
        companion object : Alive.OfThread()

        private lateinit var oldFriends: List<Friend>
        private val newFriends = arrayListOf<Friend>()
        private val reqQueue by lazy { Volley.newRequestQueue(c.c) }
        override val com: Alive.OfThread = Companion

        init {
            (c as Main).mm.unfollowers.value = null
        }

        override fun run() {
            super.run()
            runBlocking { oldFriends = c.dao.friends() }
            allFollow(theFollowers = true)
        }

        private fun allFollow(next_max_id: String = "", theFollowers: Boolean) {
            if (!active || c.m.acc == null) return
            reqQueue.adder = Api<Rest.Follow>(
                c, (if (theFollowers) Api.Endpoint.FOLLOWERS else Api.Endpoint.FOLLOWING).url
                    .format(c.m.acc?.id ?: 0, next_max_id), Rest.Follow::class,
                handler, autoQueue = false, onError = { interrupt() }
            ) { flw ->
                if (c.m.acc == null || flw.users == null) return@Api
                for (u in flw.users) {
                    val already = newFriends.indexOfFirst { it.id == u.pk }
                    if (already > -1) newFriends[already].apply {
                        if (theFollowers) follows = true
                        else followed = true
                    } else newFriends.add(
                        Friend(
                            u.pk, u.username, u.full_name!!, u.profile_pic_url, u.is_private,
                            theFollowers, !theFollowers
                        )
                    )
                }
                if (flw.next_max_id == null) {
                    if (theFollowers) allFollow(theFollowers = false)
                    else CoroutineScope(Dispatchers.IO).launch { ended() }
                } else allFollow(flw.next_max_id, theFollowers)
            }
        }

        private suspend fun ended() {
            // Update newFriends
            if (!active || c.m.acc == null || !c.db.isOpen) return
            newFriends.forEach { newer ->
                if (newer.follows) newer.unfollowedMeAt = null
                else oldFriends.find { it.id == newer.id }.also { before ->
                    if (before?.follows == true && !newer.follows)
                        newer.unfollowedMeAt = Persistent.now()
                    else newer.unfollowedMeAt = before?.unfollowedMeAt
                }
            }

            // Replace Friends
            if (!active || c.m.acc == null || !c.db.isOpen) return
            try {
                c.dao.deleteFriends()
                c.dao.addFriends(newFriends)
            } catch (e: IllegalStateException) { // DB is closed.
            } catch (e: SQLiteDatabaseLockedException) { // perhaps there were heavy transactions then.
            }

            // Update the Favourites
            if (!active || c.m.acc == null || !c.db.isOpen) return
            c.m.fav?.forEach { fav ->
                newFriends.find { fav.id == it.id }?.also { friend ->
                    fav.user = friend.user
                    fav.name = friend.name
                    fav.photo = friend.pict
                    fav.isPrivate = friend.priv
                    c.dao.updateFavourite(fav)
                }
            }

            // Notify the results
            if (!active || c.m.acc == null) return
            handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
            val newUnf = newFriends.filter {
                (it.unfollowedMeAt != null
                        && it.unfollowedMeAt!! > (c.sp?.getLong(Settings.spNotifiedUnfTill, 0L)
                    ?: 0L))
            }
            if (newUnf.isNotEmpty()) {
                withContext(Dispatchers.Main) { gotNewOnes(newUnf.size) }
                // HIGHLIGHT THEM IF YOU WANT
            }
            interrupt()
        }

        private fun gotNewOnes(num: Int) {
            if (!active || c.m.acc == null) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                (c.c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(Notify.Channel.UNF_NEW_ITEMS.create(c.c))
            NotificationManagerCompat.from(c.c).notify(
                Notify.ID_UNF_NEW_ITEMS, NotificationCompat.Builder(
                    c.c, Notify.Channel.UNF_NEW_ITEMS.id
                ).apply {
                    setSmallIcon(R.drawable.notification)
                    setContentTitle(c.c.getString(R.string.newUnfNtfChannel))
                    setContentText(c.c.getString(R.string.newUnfNtfText, num))
                    priority = NotificationCompat.PRIORITY_HIGH
                    setContentIntent(
                        PendingIntent.getActivity(
                            c.c, 0, Intent(c.c, Main::class.java)
                                .apply { putExtra(TriplePageActivity.EXTRA_TURN_TO_PAGE, 0) },
                            ForegroundService.ntfMutability()
                        )
                    )
                    setAutoCancel(true)
                }.build()
            )
            c.sp?.edit { putLong(Settings.spNotifiedUnfTill, Persistent.now()) }
        } // Never use Fragment::getString()
    }
}
