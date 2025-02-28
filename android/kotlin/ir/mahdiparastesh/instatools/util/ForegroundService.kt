package ir.mahdiparastesh.instatools.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Account.Companion.dbName
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.job.DownloadService
import ir.mahdiparastesh.instatools.job.Exporter
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.TriplePageActivity
import kotlin.reflect.KClass

/**
 * Abstract class for all foreground services in this app.
 * Most functions do not require to be called on the main thread.
 */
abstract class ForegroundService : Service(), ViewModelStoreOwner, Persistent {
    protected lateinit var ntfManager: NotificationManager

    override val viewModelStore = ViewModelStore()

    abstract val klass: Class<*>
    abstract val com: ForegroundServiceCompanion
    abstract val ntfChannel: Notify.Channel
    abstract val ntfId: Int
    abstract var ntfTitle: String
    protected open var ntfText: String? = null
    protected open var ntfSmallText: String? = null
    open val ntfSmallIcon: Int = R.drawable.notification

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private val services = arrayOf(DownloadService::class, Exporter::class)

        fun anyRunning() = arrayOf(DownloadService, Exporter).any { it.active.value == true }
        // Never reference "Downloader"'s Companion in a static variable

        fun terminateTasks(c: Context) {
            services.forEach { service ->
                c.stopService(Intent(c, service.java).apply { action = ACTION_STOP })
            }
        }

        fun ntfMutability(bb: Boolean = true): Int = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (bb) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
            else ->
                if (bb) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_IMMUTABLE
        }
    }

    /**
     * Abstract class from which all companion objects of [ForegroundService] subclasses must extend.
     */
    abstract class ForegroundServiceCompanion {
        val active = MutableLiveData(false)
    }

    // With getters and setters, you'll avoid NullPointerException;
    // Because "this" is apparently null at the time of instantiation,
    // So you cannot invoke "applicationContext" on it!
    override val c: Context get() = applicationContext
    final override val dbLazy = lazy { Database.build(c, m.acc.dbName()) }
    override val db: Database by dbLazy
    override val dao: Database.DAO by lazy { db.dao() }
    override lateinit var m: Model
    override lateinit var gsp: SharedPreferences
    override var sp: SharedPreferences? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent.action != null) when (intent.action) {
            ACTION_START -> {}
            ACTION_STOP -> onCancel()
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        com.active.value = true
        m = ViewModelProvider(viewModelStore, Model.Factory())["Model", Model::class.java]
        gsp = initGsp()
        sp = initSp(m.acc)

        ntfManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private var ntfAct: KClass<*>? = null
    private var ntfPage: Int? = null
    fun initialNotification(
        openActivity: KClass<*>? = null, turnToPage: Int? = null, progress: Pair<Int, Int>? = null
    ) {
        ntfAct = openActivity
        ntfPage = turnToPage
        ntfManager.createNotificationChannelGroup(Notify.ChannelGroup.SERVICES.create(c))
        ntfManager.createNotificationChannel(ntfChannel.create(c))
        startForeground(ntfId, notification(progress))
    }

    fun updateNotification(progress: Pair<Int, Int>? = null) {
        ntfManager.notify(ntfId, notification(progress))
    }

    private fun notification(progress: Pair<Int, Int>?) =
        NotificationCompat.Builder(c, ntfChannel.id).apply {
            setSmallIcon(ntfSmallIcon)
            setContentTitle(ntfTitle)
            ntfSmallText?.also { setContentText(it) }
            setStyle(NotificationCompat.BigTextStyle().bigText(ntfText))
            priority = NotificationCompat.PRIORITY_LOW
            // setSound(null) setSilent(true)
            setOngoing(true)
            setProgress(progress?.second ?: 0, progress?.first ?: 0, progress == null)
            if (ntfAct != null) setContentIntent(
                PendingIntent.getActivity(
                    c, 0, Intent(c, ntfAct!!.java).apply {
                        if (ntfPage != null)
                            putExtra(TriplePageActivity.Companion.EXTRA_TURN_TO_PAGE, ntfPage)
                    }, ntfMutability()
                )
            )
            addAction(0, getString(R.string.stop), pi(c, ACTION_STOP))
        }.build()

    fun pi(c: Context, code: String): PendingIntent = PendingIntent.getService(
        c, 0, Intent(c, klass).apply { action = code }, ntfMutability()
    )

    protected fun eventNotification(id: Int, func: NotificationCompat.Builder.() -> Unit) {
        ntfManager.createNotificationChannel(Notify.Channel.RESULT.create(c))
        ntfManager.notify(
            id, NotificationCompat.Builder(c, Notify.Channel.RESULT.id).apply {
                setSmallIcon(R.drawable.notification)
                func()
            }.build()
        )
    }

    @MainThread
    abstract fun onCancel()

    fun destroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        com.active.value = false
        if (dbLazy.isInitialized() && !Persistent.anyoneAlive()) db.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
