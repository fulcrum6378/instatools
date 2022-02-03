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
import ir.mahdiparastesh.instatools.databinding.LoginBinding
import ir.mahdiparastesh.instatools.databinding.WelcomeBinding
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListAcc
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import org.apache.commons.text.StringEscapeUtils

@SuppressLint("ApplySharedPref")
class Login : BaseActivity(), ViewStub.OnInflateListener {
    private lateinit var b: LoginBinding
    private lateinit var bw: WelcomeBinding
    override val menuRes: Int? = null
    private lateinit var cookieManager: CookieManager
    lateinit var accounts: ArrayList<Account>

    companion object {
        const val host = "https://www.instagram.com/"
        const val loginUrl = "${host}accounts/login/"
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
        accounts = Account.load(c)
        val selected = Account.selected(this, accounts, false)
        for (i in (accounts.size - 1) downTo 0)
            if (accounts[i].cook == null && accounts[i].id != -1L)
                accounts.removeAt(i)

        // WebView
        b.web.settings.javaScriptEnabled = true
        b.web.webViewClient = myClient

        if (selected == null) welcome() else selectAccount(selected)
    }

    private val logoDestBias = 0.15f
    override fun onInflate(stub: ViewStub, v: View) {
        bw = WelcomeBinding.bind(v)
        accounts.sortBy { it.name }
        accounts.sortBy { it.id < 0 }
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

    private fun welcome() {
        b.web.vis(false)
        if (!::bw.isInitialized) b.welcomeStub.inflate()
        else bw.root.vis()
    }

    fun selectAccount(acc: Account) {
        gonnaBeGuest = false
        when {
            acc.id == -1L -> {
                m.acc = acc
                gonnaBeGuest = true
                browse()
            }
            acc.cook != null -> browse(acc.cook)
            else -> {
                accounts.removeAll { it.id == acc.id }
                Account.save(c, accounts)
                welcome()
            }
        }
    }

    private var doClearHistory = false
    private var gonnaAdd = false
    private var gonnaBeGuest = false
    private fun browse(withCookie: String? = "", beginWith: String = loginUrl) {
        b.web.vis()
        if (::bw.isInitialized) bw.root.vis(false)
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

    private val myClient = object : WebViewClient() {
        lateinit var id: String
        var loggedIn = false

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (gonnaBeGuest) {
                id = "-1"
                accounts.getOrNull(accounts.indexOf(accounts.find { it.id == -1L }))?.cook =
                    cookieManager.getCookie(host)
                Account.save(c, accounts)
                gsp.edit().putString(spAccount, id).commit()
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
            gsp.edit().putString(spAccount, id).commit()
            v.evaluateJavascript(
                """(function() {
        return document.getElementsByTagName('body')[0].innerHTML;
    })()""".trimMargin()
            ) { html ->
                val u = Gson().fromJson(
                    StringEscapeUtils.unescapeJava(html)
                        .substringAfter(preConfig)
                        .substringBefore(posConfig),
                    HostPage::class.java
                ).config.viewer
                m.acc = Account(
                    id.toLong(), u.username, u.full_name,
                    u.profile_pic_url_hd ?: u.profile_pic_url,
                    cookieManager.getCookie(host)
                ).apply {
                    accounts.removeAll { it.id == id }
                    accounts.add(this)
                    Account.save(c, accounts)
                }
                goTo(Main::class, true)
            }
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

    data class HostPage(val config: PageConfig)

    data class PageConfig(val viewer: Profile.User)
}
