package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Process.killProcess
import android.os.Process.myPid
import android.view.View
import android.view.ViewStub
import android.webkit.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.databinding.LoginBinding
import ir.mahdiparastesh.instatools.databinding.WelcomeBinding
import ir.mahdiparastesh.instatools.json.PageConfig
import ir.mahdiparastesh.instatools.list.ListAcc
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

class Login : BaseActivity(), ViewStub.OnInflateListener {
    private lateinit var b: LoginBinding
    private lateinit var bw: WelcomeBinding
    lateinit var accounts: ArrayList<Account>
    private lateinit var cookieManager: CookieManager

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion

    companion object : ActivityCompanion() {
        const val host = "https://www.instagram.com/"
        const val rawHost = "https://instagram.com/"
        const val loginUrl = "${host}accounts/login/"
        const val spAccount = "account"
        const val EXTRA_NEED_AUTH = "needAuthentication"
        var cameHereToAuth = false

        fun CookieManager.getCookieOrganised(url: String): String {
            val raw = getCookie(url).split("; ")
            val map = HashMap<String, String>()
            for (r in raw) {
                val kv = r.split("=")
                map[kv[0]] = kv[1]
            }
            val sb = StringBuilder()
            for (e in map.entries)
                sb.append("${e.key}=${e.value}; ")
            return sb.toString().trimEnd()
        }
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
        b.web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
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
                                setMessage(getString(R.string.needAuthentication))
                                setNeutralButton(R.string.ok, null)
                            }.show()
                            val signedOutFrom =
                                if (this != -1L) accounts.find { it.id == this } else null
                            if (signedOutFrom == null || accounts.size <= 1) {
                                gonnaAdd = true
                                browse()
                            } else selectAccount(signedOutFrom)
                        }
                    else -> welcome()
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

        // Add Account
        bw.addAccount.setOnClickListener {
            gonnaAdd = true
            browse()
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
                setMessage(getString(R.string.guestSure))
                setNegativeButton(R.string.cancel, null)
                setPositiveButton(R.string.sContinue) { _, _ ->
                    m.acc = acc
                    gonnaBeGuest = true
                    browse()
                }
            }.show()
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
        if (::bw.isInitialized) bw.root.vis(false)
        cookieManager = CookieManager.getInstance().also { cm ->
            cm.setAcceptCookie(true)
            cm.removeAllCookies(object : ValueCallback<Boolean> {
                private val settable by lazy { withCookie?.split("; ") }
                private var i = 0

                override fun onReceiveValue(value: Boolean) {
                    if (!settable.isNullOrEmpty()) next() else done()
                }

                private fun next() {
                    cm.setCookie(host, settable!![i]) {
                        i++; if (settable!!.size > i) next() else done()
                    }
                }

                private fun done() {
                    b.web.loadUrl(beginWith)
                }
            })
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
                    cookieManager.getCookieOrganised(host)
                CoroutineScope(Dispatchers.IO).launch { Account.save(c, accounts) }
                gsp.edit { putString(spAccount, id) }
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

        @Throws(
            JsonSyntaxException::class, NumberFormatException::class, NullPointerException::class
        )
        private fun collect(html: String) {
            PageConfig.findConfigWrapper(html, true, {
                Delay { b.web.reload() }
                if (improperLoading < 3) improperLoading++
            }) { wrapper ->
                @Suppress("UNCHECKED_CAST")
                val config =
                    wrapper.define.find { it.firstOrNull() == "XIGSharedData" }!![2] as Map<String, Any>
                val raw = Gson().fromJson(
                    config["raw"] as String, PageConfig.RawSharedData::class.java
                )
                id = cookieManager.getCookieOrganised(host)
                    .substringAfter("ds_user_id=")
                    .substringBefore(";").toLong().toString()
                val u = raw.config.viewer
                m.acc = Account(
                    id.toLong(), u.username, u.full_name,
                    u.profile_pic_url_hd ?: u.profile_pic_url,
                    cookieManager.getCookieOrganised(host),
                    config.getOrElse("rollout_hash") { raw.rollout_hash } as String,
                    Persistent.now()
                ).apply {
                    accounts.removeAll { it.id == id }
                    accounts.add(this)
                    CoroutineScope(Dispatchers.IO).launch { Account.save(c, accounts) }
                }
                gsp.edit { putString(spAccount, id) }
                goTo(Main::class, true)
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
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
}
