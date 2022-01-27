package ir.mahdiparastesh.instatools

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class Queuer : Service() {
    private lateinit var c: Context

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // TODO: WHAT IF IT RECEIVE SHARED ITEM ITSELF?!?

        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        c = applicationContext
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
