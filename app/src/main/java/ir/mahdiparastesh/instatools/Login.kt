package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Process.killProcess
import android.os.Process.myPid
import android.view.View
import android.view.ViewStub
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.databinding.LoginBinding
import ir.mahdiparastesh.instatools.databinding.WelcomeBinding
import ir.mahdiparastesh.instatools.json.GraphQl
import ir.mahdiparastesh.instatools.list.ListAcc
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.text.StringEscapeUtils
import kotlin.system.exitProcess

class Login : BaseActivity(), ViewStub.OnInflateListener {
    private lateinit var b: LoginBinding
    private lateinit var bw: WelcomeBinding
    lateinit var accounts: ArrayList<Account>
    private lateinit var cookieManager: CookieManager
    private var adBanner: AdView? = null

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion

    companion object : ActivityCompanion() {
        const val host = "https://www.instagram.com/"
        const val rawHost = "https://instagram.com/"
        const val loginUrl = "${host}accounts/login/"
        const val spAccount = "account"
        const val EXTRA_NEED_AUTH = "needAuthentication"
        const val EXTRA_SHOW_AD = "show_ad"
        var cameHereToAuth = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = LoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.welcomeStub.setOnInflateListener(this)
        m.accountSwitched()

        // WebView
        b.web.settings.javaScriptEnabled = true
        b.web.webViewClient = myClient
        b.refresher.setOnRefreshListener { b.web.reload() }

        // Accounts
        CoroutineScope(Dispatchers.IO).launch {
            accounts = Account.load(c)
            if (accounts.find { it.id == -1L } == null)
                Account(-1L, "", "").apply { accounts.add(this) }
            withContext(Dispatchers.Main) {
                when {
                    intent.hasExtra(EXTRA_NEED_AUTH) -> intent.getLongExtra(EXTRA_NEED_AUTH, -1L)
                        .apply {
                            cameHereToAuth = true
                            AlertDialog.Builder(this@Login).apply {
                                setTitle(R.string.guest)
                                setMessage(R.string.needAuthentication)
                                setNeutralButton(R.string.ok, null)
                            }.show().stylise(this@Login)
                            val signedOutFrom =
                                if (this != -1L) accounts.find { it.id == this } else null
                            if (signedOutFrom == null || accounts.size <= 1) {
                                gonnaAdd = true
                                browse()
                            } else selectAccount(signedOutFrom)
                        }
                    else -> {
                        welcome()
                        if (intent.getBooleanExtra(EXTRA_SHOW_AD, false))
                            loadInterstitial(R.string.interAccSwitched, true)
                    }
                }
            }
        }
    }

    override fun onAccountSet() {
    }

    override fun onInflate(stub: ViewStub, v: View) {
        bw = WelcomeBinding.bind(v)
        if (night()) bw.logo.colorFilter = pdcf(R.color.defCA)
        accounts.sortByDescending { it.last.toString() }
        accounts.sortBy { it.id < 0L }
        bw.accounts.adapter = ListAcc(this)

        //bw.logo.setOnClickListener { UiTools.reviewApp(this, 0) }

        // Add Account
        bw.addAccount.setOnClickListener {
            gonnaAdd = true
            browse()
        }
        bw.addAccTv.typeface = fontRegular
    }

    private fun welcome() {
        b.refresher.vis(false)
        adBanner?.vis(false)
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
                CoroutineScope(Dispatchers.IO).launch { Account.save(c, accounts) }
                welcome()
            }
        }
    }

    private var doClearHistory = false
    private var gonnaAdd = false
    private var gonnaBeGuest = false
    private fun browse(withCookie: String? = "", beginWith: String = loginUrl) {
        b.refresher.vis()
        adBanner?.vis(true)
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

        if (areAdsReady() && adBanner == null) {
            adBanner = UiTools.adaptiveBanner(this, R.string.bnrBtmWebView)
            b.root.addView(adBanner, 1, UiTools.adaptiveBannerLp())
            adBanner!!.loadAd(AdRequest.Builder().build())
            b.refresher.layoutParams = (b.refresher.layoutParams as ConstraintLayout.LayoutParams)
                .apply { bottomToTop = R.id.adBanner }
        }
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
                CoroutineScope(Dispatchers.IO).launch { Account.save(c, accounts) }
                gsp.edit().putString(spAccount, id).apply()
                goTo(Main::class, true); return; }
            if (doClearHistory) {
                b.web.clearHistory()
                doClearHistory = false
            }
        }

        private var improperLoading = 0
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
                        // This has happened for some users with an unknown cause
                        // Also the page may have failed to load properly.
                        if (BuildConfig.DEBUG) throw e else {
                            Delay { b.web.reload() }
                            if (improperLoading < 3) improperLoading++
                        }
                    } catch (e: IllegalStateException) {
                        // The page may have failed to load properly.
                        Delay { b.web.reload() }
                        if (improperLoading < 3) improperLoading++
                        else if (BuildConfig.DEBUG) throw e
                    } catch (e: NumberFormatException) {
                        // This happens when you go to, for example, the profiles/hashtags page,
                        // tap on the pretty "Instagram" title in the header, then you go to
                        // another page, e.g. sign up page, then you come back to the same
                        // "instagram.com" page, then you repeat this act once more.
                    } catch (e: NullPointerException) {
                        // Because of an unknown reason!!
                    }
                    improperLoading = 0
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) throw e
            }
        }

        val preScheduledApplyEach = "(new ServerJS()).handleWithCustomApplyEach(ScheduledApplyEach,"

        @Throws(
            JsonSyntaxException::class, NumberFormatException::class, NullPointerException::class
        )
        private fun collect(html: String) {
            var read = html
            val scheduledApplyEach = arrayListOf<String>()
            while (read.contains(preScheduledApplyEach)) {
                read = read.substringAfter(preScheduledApplyEach)
                scheduledApplyEach.add(read.substringBefore(");});});"))
            }
            val configWrapper = scheduledApplyEach.find { it.contains("XIGSharedData") }
            if (configWrapper != null) {
                @Suppress("UNCHECKED_CAST")
                val config = Gson().fromJson(
                    StringEscapeUtils.unescapeJava(configWrapper),
                    ConfigWrapper::class.java
                ).define.find { it.firstOrNull() == "XIGSharedData" }!![2] as Map<String, Any>
                val raw = Gson().fromJson(
                    config["raw"] as String, ConfigWrapper.RawSharedData::class.java
                )
                id = cookieManager.getCookie(host)
                    .substringAfter("ds_user_id=")
                    .substringBefore(";").toLong().toString()
                val u = raw.config.viewer
                m.acc = Account(
                    id.toLong(), u.username, u.full_name,
                    u.profile_pic_url_hd ?: u.profile_pic_url, cookieManager.getCookie(host),
                    config.getOrElse("rollout_hash") { raw.rollout_hash } as String,
                    Persistent.now()
                ).apply {
                    accounts.find { it.id == id }
                        ?.also { mfrw = it.mfrw }
                    accounts.removeAll { it.id == id }
                    accounts.add(this)
                    CoroutineScope(Dispatchers.IO).launch { Account.save(c, accounts) }
                }
            } else {
                if (BuildConfig.DEBUG) throw Exception("Shared data not found!") else {
                    Delay { b.web.reload() }
                    if (improperLoading < 3) improperLoading++
                }
            }
            gsp.edit().putString(spAccount, id).apply()
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

    class ConfigWrapper(val define: Array<Array<Any>>) {

        data class RawSharedData(val config: PageConfig, val rollout_hash: String?)

        data class PageConfig(val viewer: GraphQl.User)
    }
}
