package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewStub
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import org.apache.commons.lang.StringEscapeUtils

@SuppressLint("ApplySharedPref")
class Login : BaseActivity(), ViewStub.OnInflateListener {
    private lateinit var b: LoginBinding
    private lateinit var bw: WelcomeBinding
    private lateinit var cookieManager: CookieManager
    private lateinit var db: GlobalDb
    private lateinit var dao: GlobalDb.DAO

    companion object {
        const val host = "https://www.instagram.com/"
        const val loginUrl = "${host}accounts/login/"
        private const val spCookiesBeg = "cookie_"
        const val spCookies = "$spCookiesBeg%s"
        const val spAccount = "account"
        const val preConfig = "<script type=\"text/javascript\">window._sharedData = "
        const val posConfig = ";</script>"

        fun gatherData(
            c: BaseActivity, dao: GlobalDb.DAO, guestIfNotExists: Boolean = true
        ): Account? {
            c.m.accounts = ArrayList(dao.accounts())
            if (c.m.accounts.find { it.id == -1L } == null)
                Account(-1L, "", "").apply {
                    dao.addAccount(this)
                    c.m.accounts.add(this)
                }
            return c.m.accounts.find {
                it.id == c.gsp.getString(spAccount, if (guestIfNotExists) "-1" else "-2")!!.toLong()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = LoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.welcomeStub.setOnInflateListener(this)
        db = GlobalDb.build(c).also { dao = it.dao() }
        val selected = gatherData(this, dao, false)

        // WebView
        b.web.settings.javaScriptEnabled = true
        b.web.webViewClient = myClient

        // Repair the global SharedPreferences
        gsp.all.forEach { p ->
            if (p.key.startsWith(spCookiesBeg)) try {
                val checkable = p.key.substringAfter(spCookiesBeg).toLong()
                if (m.accounts.find { it.id == checkable } == null) Account(checkable).apply {
                    dao.addAccount(this)
                    m.accounts.add(this)
                }

            } catch (ignored: NumberFormatException) {
                gsp.edit().remove(p.key).commit()
            }
        }

        if (selected == null) welcome() else selectAccount(selected)
    }

    private val logoDestBias = 0.15f
    override fun onInflate(stub: ViewStub, v: View) {
        bw = WelcomeBinding.bind(v)
        m.accounts.sortWith(Account.Sort())
        m.accounts.sortBy { it.id < 0 }
        bw.accounts.adapter = ListAcc(this)

        // Add Account
        bw.addAccount.setOnClickListener {
            gonnaAdd = true
            browse()
        }
        bw.addAccTv.typeface = fontRegular

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
        gonnaBeGuest = false
        if (acc.id == -1L) {
            m.acc = acc
            gonnaBeGuest = true
            browse()
        } else (gsp.getString(spCookies.format(acc.id), null)).apply {
            if (this != null) browse(this)
            else {
                deleteAcc(acc.id.toString())
                welcome()
            }
        }
    }

    private var doClearHistory = false
    private var gonnaAdd = false
    private var gonnaBeGuest = false
    private fun browse(withCookie: String? = "", beginWith: String = loginUrl) {
        vis(b.web)
        if (::bw.isInitialized) vis(bw.root, false)
        cookieManager = CookieManager.getInstance().also {
            it.setAcceptCookie(true)
            it.removeAllCookies { _ ->
                if (withCookie != null && withCookie != "")
                    for (k in withCookie.split("; "))
                        it.setCookie(host, k)
                b.web.loadUrl(beginWith)
            }
        }
        doClearHistory = true
    }

    private fun deleteAcc(id: String) {
        if (id == "") return
        gsp.edit().apply {
            remove(spCookies.format(id))
            if (gsp.getString(spAccount, null) == id) remove(spAccount)
                .commit()
        } // TODO: ALERT THE USER
    }

    private val myClient = object : WebViewClient() {
        lateinit var id: String
        var loggedIn = false

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (gonnaBeGuest) {
                id = "-1"
                gsp.edit()
                    .putString(spCookies.format(id), cookieManager.getCookie(host))
                    .putString(spAccount, id)
                    .commit()
                goTo(Main::class, true); return; }
            if (doClearHistory) {
                b.web.clearHistory()
                doClearHistory = false
            }
        }

        override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
            if (req.url.toString() == host) loggedIn = true
            return false
        }

        override fun onPageFinished(v: WebView, url: String) {
            super.onPageFinished(v, url)
            if (!loggedIn) return
            id = cookieManager.getCookie(host)
                .substringAfter("ds_user_id=")
                .substringBefore(";")
            gsp.edit()
                .putString(spCookies.format(id), cookieManager.getCookie(host))
                .putString(spAccount, id)
                .commit()
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
                goTo(Main::class, true)
            }
        }
    }

    data class HostPage(val config: PageConfig)

    data class PageConfig(val viewer: Profile.User)
}
