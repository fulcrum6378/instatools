package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.volley.NetworkResponse
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.databinding.PageUnfBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListUnf
import ir.mahdiparastesh.instatools.more.*
import kotlinx.coroutines.runBlocking

@Suppress("UNCHECKED_CAST")
class PageUnf(c: Main) : BasePageMain(c) {
    lateinit var b: PageUnfBinding
    private var thread: Inquiry? = null

    override val com: PageCompanion = Companion
    override lateinit var inflater: LayoutInflater
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout get() = b.root
    override val selectiveMenuRes: Int? = null
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        HANDLE_LOADED to { msg ->
            (msg.obj as List<Friend>).apply {
                c.m.unfollowers.value = ArrayList(this)
                c.m.unfollowers.value!!.sortBy { it.user }
                c.m.unfollowers.value!!.sortByDescending { it.unfollowedMeAt?.toInt() ?: 0 }
                if (isNullOrEmpty() && msg.arg1 == 1 &&
                    (Persistent.now() - (c.sp?.getLong(Settings.spUnfLastChecked, 0L)
                        ?: 0L)) > 86400000
                ) thread = Inquiry().also { it.start() }
                else onLoaded(isNullOrEmpty())
            }
        },
        HANDLE_FETCHED to {
            load(false)
            b.refresher.isRefreshing = false
            c.sp?.edit()?.putLong(Settings.spUnfLastChecked, Persistent.now())?.commit()
        },
        //HANDLE_ABORTED to { onFailed(c.getString(R.string.loadFailed)) }
        Api.HANDLE_ERROR to {
            onFailed(
                c.getString(
                    R.string.unknownError, (it.obj as NetworkResponse?)?.statusCode.toString()
                )
            )
        },
        HANDLE_COULD_NOT to {
            Snackbar.make(b.root, R.string.unfCouldNot, Snackbar.LENGTH_SHORT).show()
        }
    )

    companion object : PageCompanion() {
        const val HANDLE_LOADED = 2
        const val HANDLE_COULD_NOT = 3
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        inflater = c.themeInflater(BaseActivity.Theme.PRIMARY, inf)
        b = PageUnfBinding.inflate(inflater, parent, false)
        if (Main.guest) {
            guestMode(b.root, BaseActivity.Theme.PRIMARY); return b.root; }

        essentials()

        //b.refresher.isRefreshing = true
        if (c.m.unfollowers.value != null) onLoaded(c.m.unfollowers.value.isNullOrEmpty())
        else load(true)
        return b.root
    }

    override fun onRefresh() {
        if (thread?.active == true) return
        b.rv.adapter = null
        thread = Inquiry().also { it.start() }
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


    inner class Inquiry : BaseThread() {
        private val CHANNEL_NEW_ITEMS = "${PageUnf::class.java.`package`!!.name}.NEW_ITEMS"
        lateinit var oldFriends: List<Friend>
        var newFriends = arrayListOf<Friend>()

        init {
            c.m.unfollowers.value = null
        }

        override fun run() {
            super.run()
            runBlocking { oldFriends = c.dao.friends() }
            allFollow(theFollowers = true)
        }

        private fun allFollow(next_max_id: String = "", theFollowers: Boolean) {
            if (!active) return
            Api<Rest.Follow>(
                c, (if (theFollowers) Api.Type.FOLLOWERS else Api.Type.FOLLOWING).url
                    .format(c.m.acc!!.id, next_max_id), Rest.Follow::class,
                handler, onError = { interrupt() }
            ) { flw ->
                Thread {
                    flw.users.forEach { u ->
                        Friend.add(
                            c.dao, this, Friend(
                                u.pk, u.username, u.full_name, u.profile_pic_url, u.is_private,
                                theFollowers, !theFollowers
                            ), theFollowers
                        )
                    }
                    if (flw.next_max_id == null) {
                        if (theFollowers) allFollow(theFollowers = false) else ended()
                    } else allFollow(flw.next_max_id, theFollowers)
                }.start()
            }
        }

        private fun ended() {
            runBlocking {
                oldFriends.filter { it.id !in newFriends.map { f -> f.id } }
                    .forEach { c.dao.deleteFriend(it) }
                newFriends.forEach { newer ->
                    if (oldFriends.find { it.id == newer.id }?.follows == true && !newer.follows)
                        c.dao.updateFriend(newer.apply { unfollowedMeAt = Persistent.now() })
                }
            }
            val newUnf = newFriends.filter {
                it.unfollowedMeAt != null
                        && it.unfollowedMeAt!! > c.sp?.getLong(Settings.spNotifiedUnfTill, 0L) ?: 0L
            }
            if (newUnf.isNotEmpty()) {
                gotNewOnes(newUnf.size)
                // HIGHLIGHT THEM IF YOU WANT
            }
            handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
            interrupt()
        }

        @SuppressLint("UnspecifiedImmutableFlag")
        private fun gotNewOnes(num: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(NotificationChannel(
                        CHANNEL_NEW_ITEMS, c.getString(R.string.newUnfNtfChannel),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = c.resources.getString(R.string.newUnfNtfChannelDesc)
                    })
            with(NotificationManagerCompat.from(c)) {
                notify(368, NotificationCompat.Builder(c, CHANNEL_NEW_ITEMS).apply {
                    setSmallIcon(R.mipmap.launcher_round)
                    setContentTitle(getString(R.string.newUnfNtfChannel))
                    setContentText(getString(R.string.newUnfNtfText, num))
                    priority = NotificationCompat.PRIORITY_HIGH
                    setContentIntent(
                        PendingIntent.getActivity(
                            c, 0, Intent(c, Main::class.java)
                                .apply { putExtra(TriplePageActivity.EXTRA_TURN_TO_PAGE, 0) },
                            PendingIntent.FLAG_CANCEL_CURRENT
                        )
                    )
                }.build())
            }
            c.sp?.edit()?.putLong(Settings.spNotifiedUnfTill, Persistent.now())?.commit()
        }
    }
}
