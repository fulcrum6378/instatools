package ir.mahdiparastesh.instatools.more

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
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.serv.Follower
import ir.mahdiparastesh.instatools.serv.Queuer
import ir.mahdiparastesh.instatools.view.Notify
import kotlin.reflect.KClass

/**
 * Abstract class for all foreground services in this app.
 * This is NOT a bound service.
 */
abstract class ForegroundService : Service(), ViewModelStoreOwner, Persistent {
    lateinit var handling: HandlerThread
    private var wakeLock: PowerManager.WakeLock? = null
    protected lateinit var ntfManager: NotificationManager

    override val viewModelStore = ViewModelStore()

    abstract val com: ForegroundServiceCompanion
    abstract var ntfTitle: String
    protected open var ntfText: String? = null
    protected open var ntfSmallText: String? = null
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

        fun ntfMutability(bb: Boolean = true): Int = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (bb) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                if (bb) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_IMMUTABLE
            else -> PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    /**
     * Abstract class from which all companion objects of ForegroundService subclasses must extend.
     */
    abstract class ForegroundServiceCompanion : Alive() {
        abstract val klass: Class<*>
        abstract val channel: Notify.Channel
        open val ntfSmallIcon: Int = R.drawable.notification
        abstract val ntfId: Int
        abstract val ntfActions: Array<Pair<String, Int>>

        open fun pi(c: Context, code: String): PendingIntent = PendingIntent.getService(
            c, 0, Intent(c, klass).apply { action = code }, ntfMutability()
        )
    }

    // With getters and setters, you'll avoid NullPointerException;
    // Because "this" is apparently null at the time of instantiation,
    // So you cannot invoke "applicationContext" on it!
    override val c: Context get() = applicationContext
    final override val dbLazy = lazy { Database.build(c, (m.acc?.id ?: -1L).toString()) }
    override val db: Database by dbLazy
    override val dao: Database.DAO by lazy { db.dao() }
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
        m = ViewModelProvider(viewModelStore, Model.Factory())["Model", Model::class.java]
        gsp = initGsp()
        sp = initSp(m.acc)

        ntfManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (requiresHandling) handling = HandlerThread(com.klass.name).also { it.start() }
        if (waveLockTimeout != null) wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${com.klass.name}::lock").apply {
                    acquire(waveLockTimeout!! * 60000L)
                }
            }
    }

    private lateinit var ntfCom: ForegroundServiceCompanion
    private lateinit var ntfAct: KClass<*>
    private var ntfPage: Int? = null
    open fun initialNotification(
        com: ForegroundServiceCompanion, openActivity: KClass<*>, turnToPage: Int? = null,
        progress: Pair<Int, Int>? = null
    ) {
        ntfCom = com
        ntfAct = openActivity
        ntfPage = turnToPage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ntfManager.createNotificationChannelGroup(Notify.ChannelGroup.SERVICES.create(c))
            ntfManager.createNotificationChannel(com.channel.create(c))
        }
        startForeground(com.ntfId, notification(progress))
    }

    open fun updateNotification(progress: Pair<Int, Int>? = null) {
        ntfManager.notify(ntfCom.ntfId, notification(progress))
    }

    private fun notification(progress: Pair<Int, Int>?) =
        NotificationCompat.Builder(c, ntfCom.channel.id).apply {
            setSmallIcon(ntfCom.ntfSmallIcon)
            setContentTitle(ntfTitle)
            ntfSmallText?.also { setContentText(it) }
            setStyle(NotificationCompat.BigTextStyle().bigText(ntfText))
            priority = NotificationCompat.PRIORITY_LOW
            // setSound(null) setSilent(true)
            setOngoing(true)
            setProgress(progress?.second ?: 0, progress?.first ?: 0, progress == null)
            setContentIntent(
                PendingIntent.getActivity(
                    c, 0, Intent(c, ntfAct.java).apply {
                        if (ntfPage != null)
                            putExtra(TriplePageActivity.EXTRA_TURN_TO_PAGE, ntfPage)
                    }, ntfMutability()
                )
            )
            for (a in ntfCom.ntfActions)
                addAction(0, getString(a.second), ntfCom.pi(c, a.first))
        }.build()

    protected fun eventNotification(id: Int, func: NotificationCompat.Builder.() -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ntfManager.createNotificationChannel(Notify.Channel.RESULT.create(c))
        ntfManager.notify(
            id, NotificationCompat.Builder(c, Notify.Channel.RESULT.id).apply {
                setSmallIcon(R.drawable.notification)
                func()
            }.build()
        )
    }

    open fun onCancel() {
        finish(true)
    }

    open fun finish(cancelled: Boolean) {
        destroy()
    }

    open fun destroy() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            stopForeground(STOP_FOREGROUND_REMOVE)
        else stopForeground(true)
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
}
