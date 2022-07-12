package ir.mahdiparastesh.instatools.more

import android.os.Handler
import android.os.Looper
import android.os.Message

open class BaseThread : Thread() {
    var active = false

    override fun run() {
        active = true
    }

    override fun interrupt() {
        if (!active) return
        active = false
        super.interrupt()
    }
}

abstract class LongThread(private val looper: Looper) : BaseThread() {
    var handler: Handler? = null
    abstract val messages: Array<Pair<Int, ((msg: Message) -> Unit)>>

    override fun run() {
        active = true
        super.run()
        handler = object : Handler(looper) {
            override fun handleMessage(msg: Message) {
                messages.find { it.first == msg.what }?.second?.let { func -> func(msg) }
            }
        }
    }

    override fun interrupt() {
        handler = null
        super.interrupt()
    }
}

abstract class DbRelatedThread(val c: Persistent) : BaseThread() {
    abstract val com: Alive

    override fun run() {
        com.active.value = true
        super.run()
    }

    override fun interrupt() {
        if (com.active.value == false) return
        if (c.dbLazy.isInitialized() && !Alive.anyLiving()) c.db.close()
        com.active.value = false
        super.interrupt()
    }
}
