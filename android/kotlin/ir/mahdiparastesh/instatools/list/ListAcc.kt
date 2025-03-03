package ir.mahdiparastesh.instatools.list

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.databinding.AlsoDeleteDataBinding
import ir.mahdiparastesh.instatools.databinding.ListAccBinding
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ListAcc(val c: Login) : RecyclerView.Adapter<AnyViewHolder<ListAccBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListAccBinding> =
        AnyViewHolder(ListAccBinding.inflate(c.layoutInflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListAccBinding>, i: Int) {
        // apparently even the most static kinds of list adapters need to be null-safe.
        val acc = c.accounts.getOrNull(i) ?: return

        Glide.with(c.c).load(acc.pict).into(h.b.photo)
        h.b.name.text = acc.name
        h.b.user.text = acc.user
        h.b.name.vis(acc.name != "")
        h.b.root.setOnClickListener {
            c.accounts.getOrNull(h.layoutPosition)?.also { c.selectAccount(it) }
        }

        // clicks
        h.b.more.setOnClickListener {
            val a = c.accounts.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            more(it, a, h.layoutPosition)
        }
        h.b.root.setOnLongClickListener {
            val a = c.accounts.getOrNull(h.layoutPosition) ?: return@setOnLongClickListener true
            more(it, a, h.layoutPosition)
        }

        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.accounts.size

    private fun more(v: View, acc: Account, i: Int): Boolean {
        MaterialMenu(c, v, R.menu.acc_more,
            R.id.amOffline to {
                c.c.selectAccount(acc)
                c.c.gsp.edit { putString(Login.SP_ACCOUNT, acc.id.toString()) }
                acc.last = Utils.now()
                acc.saveMeInIO(c)
                c.goTo(Main::class, true)
            },
            R.id.amBrowseWeb to {
                c.accBrowsingWeb = acc
                c.browse(Login.BROWSE_THE_WEB, acc.cook, Login.HOST)
            },
            R.id.amInjectCookies to {
                c.injectingCookieForAccIndex = i
                c.injectCookies.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "txt"
                })
            },
            R.id.amSignOut to {
                val bd = AlsoDeleteDataBinding.inflate(c.layoutInflater)
                MaterialAlertDialogBuilder(c).apply {
                    setTitle(R.string.signOut)
                    setMessage(R.string.signOutSure)
                    setView(bd.root)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        if (acc.cook != null) CoroutineScope(Dispatchers.IO).launch {
                            Api.cookies = acc.cook ?: ""
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
                        signOut(acc, i, bd.root.isChecked)
                    }
                }.show()
            }
        ).show()
        return true
    }

    private fun signOut(acc: Account, i: Int, bd: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            if (bd) {
                Settings.deleteDb(acc.id.toString())
                Settings.deleteSp(c, acc)
            }
            c.accounts.removeAll { it.id == acc.id }
            Account.save(c, c.accounts)
            if (c.c.gsp.getString(Login.SP_ACCOUNT, null) == acc.id.toString())
                c.c.gsp.edit { remove(Login.SP_ACCOUNT) }
        }
        notifyItemRemoved(i)
        notifyItemRangeChanged(i, c.accounts.size)
        if (i > 0) notifyItemChanged(i - 1)
    }
}
