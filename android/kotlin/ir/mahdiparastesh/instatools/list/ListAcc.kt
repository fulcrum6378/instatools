package ir.mahdiparastesh.instatools.list

import android.app.AlertDialog
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.databinding.AlsoDeleteDataBinding
import ir.mahdiparastesh.instatools.databinding.ListAccBinding
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.EasyPopupMenu
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListAcc(private val c: Login) : RecyclerView.Adapter<AnyViewHolder<ListAccBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListAccBinding> =
        AnyViewHolder(ListAccBinding.inflate(c.layoutInflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListAccBinding>, i: Int) {
        // apparently even the most static kinds of list adapters need to be null-safe.
        val acc = c.accounts.getOrNull(i) ?: return

        // details
        Glide.with(c.c)
            .load(acc.pict)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(h.b.photo)
        h.b.name.text = acc.name
        h.b.user.text = acc.user
        h.b.name.vis(acc.name != "")
        h.b.root.setOnClickListener {
            if ((Utils.now() - acc.last_auth) > 86400000L)
                c.accounts.getOrNull(h.layoutPosition)?.also { c.selectAccount(it) }
            else
                enterOffline(acc)
        }

        // actions
        h.b.more.setOnClickListener {
            val a = c.accounts.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            more(it, a, h.layoutPosition)
        }
        h.b.root.setOnLongClickListener {
            val a = c.accounts.getOrNull(h.layoutPosition) ?: return@setOnLongClickListener true
            more(it, a, h.layoutPosition)
            true
        }

        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.accounts.size

    private fun more(v: View, acc: Account, i: Int) {
        EasyPopupMenu(
            c, v, R.menu.acc_more,
            R.id.amOffline to {
                enterOffline(acc)
            },
            R.id.amBrowseWeb to {
                c.accBrowsingWeb = acc
                c.browse(Login.BROWSE_THE_WEB, acc.cook, Login.IG_HOME)
            },
            R.id.amInjectCookies to {
                c.injectingCookieForAcc = acc.id
                c.injectCookies.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                })
            },
            R.id.amExportCookies to {
                if (acc.cook.isNullOrBlank())
                    Toast.makeText(c, R.string.noCookies, Toast.LENGTH_LONG).show()
                else {
                    c.injectingCookieForAcc = acc.id
                    c.exportCookies.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TITLE,
                            "Instagram cookies of @${acc.user} as of " +
                                "${Utils.fileDateTime(Utils.now())}.txt"
                        )
                    })
                }
            },
            R.id.amSignOut to {
                val bd = AlsoDeleteDataBinding.inflate(c.layoutInflater)
                AlertDialog.Builder(c).apply {
                    setTitle(R.string.signOut)
                    setMessage(R.string.signOutSure)
                    setView(bd.root)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            if (!acc.cook.isNullOrBlank()) {
                                Api.cookies = acc.cook!!
                                try {
                                    Api.json<Rest.QuickResponse>(
                                        Api.Endpoint.LOGOUT.url,
                                        true, "one_tap_app_login=1&user_id=${acc.id}"
                                    )
                                } catch (e: Api.FailureException) {
                                    if (BuildConfig.DEBUG) throw e
                                }
                                Api.cookies = ""
                            }

                            signOut(acc, i, bd.tick.isChecked)
                        }
                    }
                }.show()
            }
        ).show()
    }

    private fun enterOffline(acc: Account) {
        c.c.onLoggedIn(acc, true)
        acc.last_used = Utils.now()
        acc.saveMeInIO(c)
        c.goTo(Main::class, true)
    }

    private suspend fun signOut(acc: Account, i: Int, bd: Boolean) {
        val sid = acc.id.toString()
        if (bd) {
            c.c.storageManager.deletePickles(sid)
            c.c.storageManager.deleteSp(sid)
        }
        c.accounts.removeAll { it.id == acc.id }
        Account.save(c, c.accounts)
        if (c.c.gsp.getString(Login.SP_ACCOUNT, null) == sid)
            c.c.gsp.edit { remove(Login.SP_ACCOUNT) }

        withContext(Dispatchers.Main) {
            notifyItemRemoved(i)
            val total = c.accounts.size
            if (total > i + 1) notifyItemRangeChanged(i, total - i - 1)
            else if (i > 0) notifyItemChanged(i - 1)
        }
    }
}
