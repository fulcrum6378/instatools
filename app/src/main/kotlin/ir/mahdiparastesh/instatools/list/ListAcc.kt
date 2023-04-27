package ir.mahdiparastesh.instatools.list

import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.databinding.AlsoDeleteDataBinding
import ir.mahdiparastesh.instatools.databinding.ListAccBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.view.Act
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
        // Apparently even the most static kinds of list adapters need to be null-safe.
        val acc = c.accounts.getOrNull(i) ?: return
        val guest = acc.id < 0L

        Glide.with(c.c).load(if (!guest) acc.pict else R.mipmap.launcher)
            .into(h.b.photo)
        if (!guest) h.b.name.text = acc.name
        else h.b.name.setText(R.string.guest)
        if (!guest) h.b.user.text = acc.user
        else h.b.user.setText(R.string.enterWithoutAuth)
        h.b.name.vis(guest || acc.name != "")
        h.b.root.setOnClickListener {
            c.accounts.getOrNull(h.layoutPosition)?.also { c.selectAccount(it) }
        }

        // Clicks
        h.b.more.vis(!guest)
        h.b.more.setOnClickListener(if (!guest) View.OnClickListener {
            val a = c.accounts.getOrNull(h.layoutPosition) ?: return@OnClickListener
            more(it, a, h.layoutPosition)
        } else null)
        h.b.root.setOnLongClickListener(if (!guest) View.OnLongClickListener {
            val a = c.accounts.getOrNull(h.layoutPosition) ?: return@OnLongClickListener true
            more(it, a, h.layoutPosition)
        } else null)

        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.accounts.size

    private fun more(v: View, acc: Account, i: Int): Boolean {
        MaterialMenu(c, v, R.menu.acc_more, Act().apply {
            this[R.id.amWithoutAuth] = {
                c.gsp.edit { putString(Login.spAccount, acc.id.toString()) }
                c.goTo(Main::class, true)
            }
            this[R.id.amBrowseWeb] = {
                c.browse(c.BROWSE_THE_WEB, acc.cook, Login.host)
            }
            this[R.id.amSignOut] = {
                val bd = AlsoDeleteDataBinding.inflate(c.layoutInflater)
                MaterialAlertDialogBuilder(c).apply {
                    setTitle(R.string.signOut)
                    setMessage(R.string.signOutSure)
                    setView(bd.root)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        Api<Rest.Signing>(
                            c, Api.Endpoint.SIGN_OUT.url, Rest.Signing::class, null,
                            "one_tap_app_login=1&user_id=${acc.id}",
                            method = Request.Method.POST,
                            acc = acc,
                            onError = { signOut(acc, i, bd.root.isChecked) }
                        ) { signOut(acc, i, bd.root.isChecked) }
                    }
                }.show()
            }
        }).show()
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
            if (c.gsp.getString(Login.spAccount, null) == acc.id.toString())
                c.gsp.edit { remove(Login.spAccount) }
        }
        notifyItemRemoved(i)
        notifyItemRangeChanged(i, c.accounts.size)
        if (i > 0) notifyItemChanged(i - 1)
    }
}
