package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Process.killProcess
import android.os.Process.myPid
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewStub
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.databinding.LoginBinding
import ir.mahdiparastesh.instatools.databinding.WelcomeBinding
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListAcc
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import org.apache.commons.text.StringEscapeUtils
import kotlin.system.exitProcess

class Login : BaseActivity(), ViewStub.OnInflateListener {
    private lateinit var b: LoginBinding
    private lateinit var bw: WelcomeBinding
    private lateinit var cookieManager: CookieManager
    lateinit var accounts: ArrayList<Account>

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion

    companion object : ActivityCompanion() {
        const val host = "https://www.instagram.com/"
        const val rawHost = "https://instagram.com/"
        const val loginUrl = "${host}accounts/login/"
        const val spAccount = "account"
        const val preConfig = "<script type=\"text/javascript\">window._sharedData = "
        const val posConfig = ";</script>"
        const val EXTRA_NEED_AUTH = "needAuthentication"
        var cameHereToAuth = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = LoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.welcomeStub.setOnInflateListener(this)
        accounts = Account.load(c)
        if (accounts.find { it.id == -1L } == null)
            Account(-1L, "", "").apply { accounts.add(this) }

        // WebView
        b.web.settings.javaScriptEnabled = true
        b.web.webViewClient = myClient
        b.refresher.setOnRefreshListener { b.web.reload() }

        when {
            intent.getBooleanExtra(EXTRA_NEED_AUTH, false) -> { // if (accounts.size <= 1)
                cameHereToAuth = true
                Snackbar.make(b.root, R.string.needAuthentication, Snackbar.LENGTH_LONG).show()
                gonnaAdd = true
                browse()
            }
            //m.acc != null -> selectAccount(m.acc!!)
            else -> welcome()
        }
    }

    private val logoDestBias = 0.15f
    override fun onInflate(stub: ViewStub, v: View) {
        bw = WelcomeBinding.bind(v)
        if (night()) bw.logo.colorFilter = pdcf(R.color.defCA)
        accounts.sortByDescending { (it.last ?: 0L).toString() }
        accounts.sortBy { it.id < 0L }
        bw.accounts.adapter = ListAcc(this)

        // Add Account
        bw.addAccount.setOnClickListener {
            gonnaAdd = true
            browse()
        }
        bw.addAccTv.typeface = fontRegular

        // Animate
        if (!m.loginLoaded) Delay(300) {
            val cs = ConstraintSet()
            cs.clone(bw.root)
            TransitionManager.beginDelayedTransition(bw.root, AutoTransition().setDuration(900))
            cs.setVerticalBias(bw.logo.id, logoDestBias)
            cs.applyTo(bw.root)
            m.loginLoaded = true
        } else bw.logo.layoutParams =
            (bw.logo.layoutParams as ConstraintLayout.LayoutParams).apply {
                verticalBias = logoDestBias
            }
    }

    private fun welcome() {
        b.refresher.vis(false)
        if (!::bw.isInitialized) b.welcomeStub.inflate()
        else bw.root.vis()
    }

    fun selectAccount(acc: Account) {
        gonnaBeGuest = false
        when {
            acc.id == -1L -> AlertDialog.Builder(this).apply {
                setTitle(R.string.guest)
                setMessage(R.string.guestSure)
                setNegativeButton(R.string.cancel, null)
                setPositiveButton(R.string.sContinue) { _, _ ->
                    m.acc = acc
                    gonnaBeGuest = true
                    browse()
                }
            }.show().stylise(this)
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
        b.refresher.vis()
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

    override fun onDestroy() {
        cameHereToAuth = false
        super.onDestroy()
    }

    private val myClient = object : WebViewClient() {
        lateinit var id: String

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            b.refresher.isRefreshing = true
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

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            b.refresher.isRefreshing = false
            if (url != host && !url.startsWith("$host?")) return
            try { // Don't remove the explanatory comments
                view.evaluateJavascript(
                    """(function() {
        return document.getElementsByTagName('body')[0].innerHTML;
    })()""".trimMargin()
                ) { html ->
                    try {
                        collect(html)
                    } catch (e: JsonSyntaxException) {
                        if (BuildConfig.DEBUG) throw e
                        // This has happened for some users with an unknown cause
                    } catch (e: NumberFormatException) {
                        // This happens when you go to, for example, the profiles/hashtags page,
                        // tap on the pretty "Instagram" title in the header, then you go to
                        // another page, e.g. sign up page, then you come back to the same
                        // "instagram.com" page, then you repeat this act once more.
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) throw e
            }
        }

        @Throws(JsonSyntaxException::class, NumberFormatException::class)
        private fun collect(html: String) {
            val profile = Gson().fromJson(
                StringEscapeUtils.unescapeJava(html)
                    .substringAfter(preConfig)
                    .substringBefore(posConfig),
                HostPage::class.java
            )
            val u = profile.config?.viewer ?: return
            id = cookieManager.getCookie(host)
                .substringAfter("ds_user_id=")
                .substringBefore(";").toLong().toString()
            m.acc = Account(
                id.toLong(), u.username, u.full_name,
                u.profile_pic_url_hd ?: u.profile_pic_url,
                cookieManager.getCookie(host), profile.rollout_hash,
                Persistent.now()
            ).apply {
                accounts.removeAll { it.id == id }
                accounts.add(this)
                Account.save(c, accounts)
            }
            gsp.edit().putString(spAccount, id).commit()
            goTo(Main::class, true)
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
        moveTaskToBack(true)
        killProcess(myPid())
        exitProcess(0)
    }

    data class HostPage(val config: PageConfig?, val rollout_hash: String?)

    data class PageConfig(val viewer: Profile.User?)
}
