package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewStub
import android.webkit.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.gson.Gson
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.GlobalDb
import ir.mahdiparastesh.instatools.databinding.LoginBinding
import ir.mahdiparastesh.instatools.databinding.WelcomeBinding
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListAcc
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.DbFile
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.UiTools.Companion.vis
import org.apache.commons.lang.StringEscapeUtils
import java.util.*

class Login : BaseActivity(), ViewStub.OnInflateListener {
    private lateinit var b: LoginBinding
    private lateinit var bw: WelcomeBinding
    private lateinit var cookieManager: CookieManager
    private lateinit var db: GlobalDb
    private lateinit var dao: GlobalDb.DAO

    companion object {
        const val host = "https://www.instagram.com/"
        const val spCookiesBeg = "cookie_"
        const val spCookies = "$spCookiesBeg%s"
        const val spAccount = "account"
        const val preConfig = "<script type=\"text/javascript\">window._sharedData = "
        const val posConfig = ";</script>"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = LoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.welcomeStub.setOnInflateListener(this)

        // WebView
        b.web.settings.javaScriptEnabled = true
        b.web.webViewClient = myClient

        // Repair SharedPreferences
        sp.all.forEach { p ->
            if (p.key.startsWith(spCookiesBeg)) try {
                val checkable = p.key.substringAfter(spCookiesBeg).toLong()
                if (m.accounts.find { it.id == checkable } == null) repairAcc(checkable)
            } catch (ignored: NumberFormatException) {
                sp.edit().remove(p.key).apply()
            }
        }

        // Repair Databases
        for (d in c.databaseList()) if (
            !d.startsWith(GlobalDb.file) &&
            !d.endsWith(DbFile.Triple.SHARED_MEMORY.s)
            && !d.endsWith(DbFile.Triple.WRITE_AHEAD_LOG.s)
        ) try {
            var name = d
            if (name.endsWith(".db")) name = name.substringBefore(".db")
            val dId = name.toLong()
            if (m.accounts.find { it.id == dId } == null) repairAcc(dId)
        } catch (ignored: NumberFormatException) {
            DbFile(d, DbFile.Triple.MAIN).delete()
            DbFile(d, DbFile.Triple.SHARED_MEMORY).apply { if (exists()) delete() }
            DbFile(d, DbFile.Triple.WRITE_AHEAD_LOG).apply { if (exists()) delete() }
        }

        // Gather Data
        db = GlobalDb.build(c).also { dao = it.dao() }
        m.accounts = ArrayList(dao.accounts())
        if (m.accounts.find { it.id == -1L } == null)
            Account(-1L, "", "").apply {
                dao.addAccount(this)
                m.accounts.add(this)
            }

        // Decide
        if (!sp.contains(spAccount)) welcome()
        else {
            val selected =
                m.accounts.find { it.id == sp.getString(spAccount, "IMPOSSIBLE")!!.toLong() }
            if (selected != null) selectAccount(selected)
            else welcome()
        }
    }

    private val logoDestBias = 0.15f
    override fun onInflate(stub: ViewStub, v: View) {
        bw = WelcomeBinding.bind(v)
        bw.accounts.adapter = ListAcc(this)
        bw.addAccount.setOnClickListener {
            gonnaAdd = true
            browse()
        }

        // Animate
        if (!m.loginLoaded) Delay(1500) {
            val cs = ConstraintSet()
            cs.clone(bw.root)
            TransitionManager.beginDelayedTransition(bw.root, AutoTransition().setDuration(800))
            cs.setVerticalBias(bw.logo.id, logoDestBias)
            cs.applyTo(bw.root)
            m.loginLoaded = true
        } else bw.logo.layoutParams =
            (bw.logo.layoutParams as ConstraintLayout.LayoutParams).apply {
                verticalBias = logoDestBias
            }
    }

    override fun onBackPressed() {
        if (b.web.canGoBack()) {
            b.web.goBack(); return; }
        if (gonnaAdd) {
            gonnaAdd = false
            b.web.loadUrl("")
            welcome()
            return; }
        super.onBackPressed()
    }

    private fun welcome() {
        vis(b.web, false)
        if (!::bw.isInitialized) b.welcomeStub.inflate()
        else vis(bw.root)
    }

    fun selectAccount(acc: Account) {
        if (acc.id == -1L) {
            m.acc = acc
            goAhead()
        } else (sp.getString(spCookies.format(acc.id), null)).apply {
            if (this != null) browse(this)
            else {
                deleteAcc(acc.id.toString())
                welcome()
            }
        }
    }

    private var doClearHistory = false
    private var gonnaAdd = false
    private val loginUrl = "${host}accounts/login/"
    private fun browse(withCookie: String? = "") {
        vis(b.web)
        if (::bw.isInitialized) vis(bw.root, false)
        cookieManager = CookieManager.getInstance().also {
            it.setAcceptCookie(true)
            it.removeAllCookies { _ ->
                if (withCookie == null) return@removeAllCookies
                val cook = withCookie.split("; ")
                for (k in cook) it.setCookie(host, k)
                b.web.loadUrl(loginUrl)
            }
        }
        doClearHistory = true
    }

    private fun goAhead() {
        if (m.acc!!.id != -1L) sp.edit().apply {
            putString(spAccount, m.acc!!.id.toString())
            apply()
        }
        startActivity(Intent(c, Main::class.java))
        finish()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun repairAcc(id: Long) = Account(id).apply {
        dao.addAccount(this)
        m.accounts.add(this)
        if (::bw.isInitialized) bw.accounts.adapter?.notifyDataSetChanged()
    }

    private fun deleteAcc(id: String) {
        if (id == "") return
        sp.edit().apply {
            remove(spCookies.format(id))
            if (sp.getString(spAccount, null) == id) remove(spAccount)
            apply()
        }
        // TODO: ALERT THE USER
    }

    private val myClient = object : WebViewClient() {
        lateinit var id: String
        var loggedIn = false

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (doClearHistory) {
                b.web.clearHistory()
                doClearHistory = false
            }
        }

        override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
            if (req.url.toString() == host) {
                loggedIn = true
                id = cookieManager.getCookie(host)
                    .substringAfter("ds_user_id=")
                    .substringBefore(";")
                sp.edit()
                    .putString(spCookies.format(id), cookieManager.getCookie(host))
                    .apply()
            }
            return false
        }

        override fun onPageFinished(v: WebView, url: String) {
            super.onPageFinished(v, url)
            if (!loggedIn) return
            v.evaluateJavascript(
                """(function() {
        return document.getElementsByTagName('body')[0].innerHTML;
    })()""".trimMargin()
            ) {
                val u = Gson().fromJson(
                    StringEscapeUtils.unescapeJava(it)
                        .substringAfter(preConfig)
                        .substringBefore(posConfig),
                    HostPage::class.java
                ).config.viewer
                Account(
                    id.toLong(), u.username, u.full_name, u.profile_pic_url_hd ?: u.profile_pic_url
                ).apply {
                    dao.addAccount(this)
                    m.acc = this
                }
                goAhead()
            }
        }
    }

    data class HostPage(val config: PageConfig)

    data class PageConfig(val viewer: Profile.User)
}
