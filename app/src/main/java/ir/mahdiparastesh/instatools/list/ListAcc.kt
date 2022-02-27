package ir.mahdiparastesh.instatools.list

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.databinding.AlsoDeleteDataBinding
import ir.mahdiparastesh.instatools.databinding.ListAccBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

class ListAcc(val c: Login) : RecyclerView.Adapter<ListAcc.ViewHolder>() {
    class ViewHolder(val b: ListAccBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListAccBinding.inflate(c.layoutInflater, parent, false)
        b.name.typeface = c.fontRegular
        b.user.typeface = c.fontRegular
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        val guest = c.accounts[i].id < 0L
        Glide.with(c.c).load(if (!guest) c.accounts[i].pict else R.mipmap.launcher)
            .into(h.b.photo)
        if (!guest) h.b.name.text = c.accounts[i].name
        else h.b.name.setText(R.string.guest)
        if (!guest) h.b.user.text = c.accounts[i].user
        else h.b.user.setText(R.string.guestShortDesc)
        h.b.name.vis(guest || c.accounts[i].name != "")
        h.b.root.setOnClickListener {
            c.selectAccount(c.accounts[h.layoutPosition])
        }

        // Clicks
        h.b.more.vis(!guest)
        h.b.more.setOnClickListener(if (!guest) View.OnClickListener {
            more(it, c.accounts[h.layoutPosition], h.layoutPosition)
        } else null)
        h.b.root.setOnLongClickListener(if (!guest) View.OnLongClickListener {
            more(it, c.accounts[h.layoutPosition], h.layoutPosition)
        } else null)

        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.accounts.size

    private fun more(v: View, acc: Account, i: Int): Boolean {
        MaterialMenu(c, v, R.menu.acc_more, Act().apply {
            this[R.id.amSignOut] = {
                val bd = AlsoDeleteDataBinding.inflate(c.layoutInflater)
                bd.root.typeface = c.fontRegular
                AlertDialog.Builder(c).apply {
                    setTitle(R.string.signOut)
                    setMessage(R.string.signOutSure)
                    setView(bd.root)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        Api<Rest.Signing>(
                            c, Api.Type.SIGN_OUT.url, Rest.Signing::class, null,
                            "one_tap_app_login=1&user_id=${acc.id}",
                            method = Request.Method.POST,
                            acc = acc,
                            onError = { signOut(acc, i, bd.root.isChecked) }
                        ) { signOut(acc, i, bd.root.isChecked) }
                    }
                }.show().stylise(c)
            }
        }).show()
        return true
    }

    private fun signOut(acc: Account, i: Int, bd: Boolean) {
        if (bd) {
            Settings.deleteDb(acc.id.toString())
            Settings.deleteSp(c, acc)
        }
        c.accounts.removeAll { it.id == acc.id }
        Account.save(c, c.accounts)
        if (c.gsp.getString(Login.spAccount, null) == acc.id.toString())
            c.gsp.edit().remove(Login.spAccount).commit()
        notifyItemRemoved(i)
        notifyItemRangeChanged(i, c.accounts.size)
        if (i > 0) notifyItemChanged(i - 1)
    }
}
