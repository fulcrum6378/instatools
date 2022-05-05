package ir.mahdiparastesh.instatools.more

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.serv.Follower
import ir.mahdiparastesh.instatools.serv.Queuer
import kotlin.reflect.KClass

@SuppressLint("UnspecifiedImmutableFlag")
abstract class ForegroundService : Service(), ViewModelStoreOwner, Persistent {
    private var mViewModelStore = ViewModelStore()
    private val dbLazy = lazy { Database.build(c, (m.acc?.id ?: -1L).toString()) }
    private val db: Database by dbLazy
    val dao: Database.DAO by lazy { db.dao() }
    lateinit var handling: HandlerThread
    private var wakeLock: PowerManager.WakeLock? = null

    abstract val com: ForegroundServiceCompanion
    abstract val requiresHandling: Boolean
    open val waveLockTimeout: Int? = null // in minutes

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private val services = arrayOf(Queuer::class, Exporter::class, Follower::class)

        fun anyRunning() = arrayOf(Queuer, Exporter, Follower).any { it.active.value!! }
        // Never reference "Queuer"'s Companion in a static variable

        fun terminateTasks(c: Context) {
            services.forEach { service ->
                c.stopService(Intent(c, service.java).apply { action = ACTION_STOP })
            }
        }
    }

    abstract class ForegroundServiceCompanion(val CH_ID: Int, private val klass: KClass<*>) :
        Alive() {
        val pack: String = klass.java.`package`!!.name
        abstract val channel: String
        abstract val chName: Int
        abstract val chDesc: Int
        abstract val ntfSmallIcon: Int
        abstract val ntfTitle: Int
        abstract val ntfActions: Array<Pair<String, Int>>

        open fun pi(c: Context, code: String): PendingIntent = PendingIntent.getService(
            c, 0, Intent(c, klass.java).apply { action = code },
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    // With getters and setters, you'll avoid NullPointerException;
    // Because "this" is apparently null at the time of instantiation,
    // So you cannot invoke "applicationContext" on it!
    override val c: Context get() = applicationContext
    override lateinit var m: Model
    override lateinit var gsp: SharedPreferences
    override var sp: SharedPreferences? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent.action != null) when (intent.action) {
            ACTION_START -> resolveIntent(intent)
            ACTION_STOP -> if (com.active.value!!) onCancel()
        }
        return START_NOT_STICKY
    }

    open fun resolveIntent(intent: Intent) {
    }

    override fun onCreate() {
        super.onCreate()
        com.active.value = true
        m = ViewModelProvider(viewModelStore, Model.Factory()).get("Model", Model::class.java)
        gsp = initGsp()
        sp = initSp(m.acc)

        if (requiresHandling) handling = HandlerThread(com.pack).also { it.start() }
        if (waveLockTimeout != null) wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${com.pack}::lock").apply {
                    acquire(waveLockTimeout!! * 60000L)
                }
            }
    }

    open fun notification(
        com: ForegroundServiceCompanion, openActivity: KClass<*>, turnToPage: Int? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(
                        com.channel, c.resources.getString(com.chName),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = c.resources.getString(com.chDesc) })
        startForeground(com.CH_ID, NotificationCompat.Builder(c, com.channel).apply {
            setSmallIcon(com.ntfSmallIcon)
            setContentTitle(c.resources.getString(com.ntfTitle))
            setOngoing(true)
            setProgress(0, 0, true)
            priority = NotificationCompat.PRIORITY_LOW
            setContentIntent(
                PendingIntent.getActivity(
                    c, 0, Intent(c, openActivity.java).apply {
                        if (turnToPage != null)
                            putExtra(TriplePageActivity.EXTRA_TURN_TO_PAGE, turnToPage)
                    }, PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            for (a in com.ntfActions)
                addAction(0, c.resources.getString(a.second), com.pi(c, a.first))
        }.build())
    }

    open fun onCancel() {
        finish(true)
    }

    open fun finish(cancelled: Boolean) {
        destroy()
    }

    open fun destroy() {
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        if (waveLockTimeout != null) wakeLock?.let { if (it.isHeld) it.release() }
        if (requiresHandling) handling.quitSafely()

        com.handler = null
        com.active.value = false
        if (dbLazy.isInitialized() && !Alive.anyLiving()) db.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun getViewModelStore(): ViewModelStore = mViewModelStore
}
