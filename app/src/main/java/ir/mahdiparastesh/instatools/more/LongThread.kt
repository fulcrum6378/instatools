package ir.mahdiparastesh.instatools.more

import android.os.Handler
import android.os.HandlerThread
import android.os.Message

abstract class LongThread(name: String) : HandlerThread(name) {
    var active = false
    var handler: Handler? = null
    abstract val messages: Array<Pair<Int, ((msg: Message) -> Unit)>>

    override fun run() {
        active = true
        super.run()
        handler = object : Handler(looper) {
            override fun handleMessage(msg: Message) {
                throw Exception("${msg.what}")
                messages.find { it.first == msg.what }?.second?.let { func -> func(msg) }
            }
        }
    }

    override fun interrupt() {
        quitSafely()
        handler = null
        active = false
        super.interrupt()
    }
}
