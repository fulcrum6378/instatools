package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.data.PersonalDb
import ir.mahdiparastesh.instatools.data.Unfollower
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.Persistent

@SuppressLint("UnspecifiedImmutableFlag")
class Inquisitor : Service(), ViewModelStoreOwner, Persistent {
    private var mViewModelStore = ViewModelStore()
    private lateinit var pDb: PersonalDb
    private lateinit var pDao: PersonalDb.DAO
    private var inquiry: Inquiry? = null

    override var c: Context
        get() = applicationContext
        set(_) {}
    override var m: Model
        get() = ViewModelProvider(viewModelStore, Model.Factory()).get("Model", Model::class.java)
        set(_) {}
    override var gsp: SharedPreferences
        get() = Persistent.initGsp(c)
        set(_) {}
    override var sp: SharedPreferences?
        get() = Persistent.initSp(c, m.acc)
        set(_) {}

    companion object {
        private val pack: String = Inquisitor::class.java.`package`!!.name
        val CHANNEL = "$pack.INQUIRING"
        val ACTION_STOP = "${pack}.ACTION_STOP"
        const val CH_ID = 286
        var active = false
        var handler: Handler? = null

        fun pi(c: Context, code: String): PendingIntent = PendingIntent.getService(
            c, 0, Intent(c, Inquisitor::class.java).apply { action = code },
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent.action != null) when (intent.action) {
            ACTION_STOP -> if (active) stopForeground(true)
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        active = true
        if (m.acc == null) stopSelf()
        pDb = PersonalDb.build(c, m.acc!!.id.toString()).also { pDao = it.dao() }
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    Api.HANDLE_ERROR -> {
                        PageUnf.theHandler?.obtainMessage(PageUnf.Action.ABORTED.ordinal)
                            ?.sendToTarget()
                        inquiry?.interrupt()
                        stopForeground(true)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL, c.resources.getString(R.string.inquiryChannel),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = c.resources.getString(R.string.inquiryChannelDesc) })
        startForeground(CH_ID, NotificationCompat.Builder(c, CHANNEL).apply {
            setSmallIcon(R.mipmap.launcher_round)
            setContentTitle(c.resources.getString(R.string.inquiryTitle))
            setOngoing(true)
            priority = NotificationCompat.PRIORITY_LOW
            setContentIntent(
                PendingIntent.getActivity(
                    c, 0, Intent(c, Downloads::class.java), PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            addAction(0, c.resources.getString(R.string.inquiryStop), pi(c, ACTION_STOP))
        }.build())

        inquiry = Inquiry().also { it.start() }
    }

    override fun onDestroy() {
        handler = null
        inquiry?.interrupt()
        stopForeground(true)
        super.onDestroy()
        active = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun getViewModelStore(): ViewModelStore = mViewModelStore

    inner class Inquiry : BasePage.BaseThread() {
        private val following = arrayListOf<Rest.User>()

        override fun run() {
            super.run()
            allFollow()
        }

        private fun allFollow(next_max_id: String = "") {
            if (!active) return
            Api<Rest.Follow>(
                this@Inquisitor,
                Api.Type.FOLLOWING.url.format(m.acc!!.id, next_max_id),
                Rest.Follow::class
            ) { flw ->
                following.addAll(flw.users.toMutableList())
                if (flw.next_max_id == null) analyse()
                else Delay { allFollow(flw.next_max_id) }
            }
        }

        private fun analyse(i: Int = 0) {
            if (!active) return
            if (i >= following.size) {
                PageUnf.theHandler?.obtainMessage(PageUnf.Action.COMPLETED.ordinal)?.sendToTarget()
                stopForeground(true)
                return
            }
            Api<Profile>(
                this@Inquisitor, Api.Type.PROFILE.url.format(following[i].username), Profile::class,
                handleError = handler
            ) { profile ->
                val u = profile.graphql?.user
                if (u == null || u.follows_viewer != false || !active) return@Api
                val newbie = Unfollower(
                    u.id.toLong(), u.username, u.full_name, u.profile_pic_url,
                    u.edge_followed_by.count.toLong(), u.is_private == true
                )
                pDao.addUnfollower(newbie)
                PageUnf.theHandler?.obtainMessage(PageUnf.Action.ANALYSED.ordinal, newbie)
                    ?.sendToTarget()
            }
            Delay(500) { analyse(i + 1) }
        }
    }
}
