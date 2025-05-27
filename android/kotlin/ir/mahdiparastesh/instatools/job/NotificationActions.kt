package ir.mahdiparastesh.instatools.job

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import ir.mahdiparastesh.instatools.InstaTools
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.view.Notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActions : BroadcastReceiver() {

    companion object {
        const val FORGET_COMMANDS = "ir.mahdiparastesh.instatools.FORGET_COMMANDS"
        const val EXTRA_ACC_ID = "acc_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val c = context.applicationContext as InstaTools

        when (intent.action) {
            FORGET_COMMANDS -> {
                val accId = intent.getLongExtra(EXTRA_ACC_ID, -1L)
                (c.getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(Notify.ID_COMMANDER_PAUSED)
                if (accId != -1L) CoroutineScope(Dispatchers.IO).launch {
                    if (c.areCommandsLoaded())
                        c.commands.forget<Command>()  // doesn't write to the pickle
                    Pickle(c.filesDir, accId, Pickle.Type.COMMAND_LIST, null)
                        .leaf.delete()
                }
            }
        }
    }
}
