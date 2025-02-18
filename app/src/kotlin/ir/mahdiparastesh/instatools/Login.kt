package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Process.killProcess
import android.os.Process.myPid
import android.view.View
import android.view.ViewStub
import android.webkit.*
import android.widget.Toast
import androidx.core.content.edit
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import ir.mahdiparastesh.instatools.api.PageConfig
import ir.mahdiparastesh.instatools.api.User
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.databinding.LoginBinding
import ir.mahdiparastesh.instatools.databinding.WelcomeBinding
import ir.mahdiparastesh.instatools.list.ListAcc
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.Delay
import ir.mahdiparastesh.instatools.util.Persistent
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import kotlin.system.exitProcess

class Login : BaseActivity(), ViewStub.OnInflateListener {
    private lateinit var b: LoginBinding
    private lateinit var bw: WelcomeBinding
    lateinit var accounts: ArrayList<Account>
    private lateinit var cookieManager: CookieManager
    var injectingCookieForAccIndex: Int? = null

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion

    companion object : ActivityCompanion() {
        const val HOST = "https://www.instagram.com/"
        const val RAW_HOST = "https://instagram.com/"
        const val LOGIN_URL = "${HOST}accounts/login/"
        const val SP_ACCOUNT = "account" // String
        const val EXTRA_NEED_AUTH = "needAuthentication"
        const val BROWSE_FOR_ADD = 0
        const val BROWSE_AS_GUEST = 1
        const val BROWSE_ACC_EXIST = 2
        const val BROWSE_AUTH_REQ = 3
        const val BROWSE_THE_WEB = 4
        var browsePurpose: Int? = null
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
        b.web.settings.domStorageEnabled = true
        b.web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        b.web.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/133.0.0.0 Safari/537.36"
        b.web.webViewClient = myClient
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && night()) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(b.web.settings, true)
            else b.web.isForceDarkAllowed = true
        }
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
                            MaterialAlertDialogBuilder(this@Login).apply {
                                setTitle(R.string.guest)
                                setMessage(getString(R.string.needAuthentication))
                                setNeutralButton(R.string.ok, null)
                            }.show()
                            val signedOutFrom =
                                if (this != -1L) accounts.find { it.id == this } else null
                            if (signedOutFrom == null || accounts.size <= 1) {
                                browse(BROWSE_AUTH_REQ)
                            } else selectAccount(signedOutFrom)
                        }
                    else -> welcome()
                }
            }
        }
    }

    override fun onAccountSet() {}
    override fun onInflate(stub: ViewStub, v: View) {
        bw = WelcomeBinding.bind(v)
        if (night()) bw.logo.colorFilter = pdcf(R.color.defCA)
        accounts.sortByDescending { it.last.toString() }
        accounts.sortBy { it.id < 0L }
        bw.accounts.adapter = ListAcc(this)

        // Add Account
        bw.addAccount.setOnClickListener { browse(BROWSE_FOR_ADD) }
    }

    private fun welcome() {
        b.refresher.vis(false)
        if (!::bw.isInitialized) b.welcomeStub.inflate()
        else bw.root.vis()
    }

    val injectCookies = launcherForResult {
        val acc = injectingCookieForAccIndex?.let { accounts.getOrNull(it) }
        if (it.resultCode != RESULT_OK || acc == null) return@launcherForResult
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                contentResolver.openFileDescriptor(it.data!!.data!!, "r").use { des ->
                    FileInputStream(des!!.fileDescriptor).readBytes().toString(Charsets.UTF_8)
                }
            }.onSuccess { cookies ->
                acc.cook = cookies
                acc.saveMe(c)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        c, R.string.cookieInjectionSuccess, Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure {
                injectingCookieForAccIndex = null
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        c, R.string.importReadError, Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun selectAccount(acc: Account) {
        when {
            acc.id == -1L -> MaterialAlertDialogBuilder(this).apply {
                setTitle(R.string.guest)
                setMessage(getString(R.string.guestSure))
                setNegativeButton(R.string.cancel, null)
                setPositiveButton(R.string.sContinue) { _, _ ->
                    m.acc = acc
                    browse(BROWSE_AS_GUEST)
                }
            }.show()
            acc.cook != null -> browse(BROWSE_ACC_EXIST, acc.cook)
            else -> {
                accounts.removeAll { it.id == acc.id }
                CoroutineScope(Dispatchers.IO).launch { Account.save(c, accounts) }
                welcome()
            }
        }
    }

    private var doClearHistory = false
    fun browse(purpose: Int, withCookie: String? = "", beginWith: String = LOGIN_URL) {
        browsePurpose = purpose
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
                    cm.setCookie(HOST, settable!![i]) {
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

    private val myClient = object : WebViewClient() {
        lateinit var id: String

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            b.refresher.isRefreshing = true
            if (browsePurpose == BROWSE_AS_GUEST) {
                id = "-1"
                accounts.getOrNull(accounts.indexOf(accounts.find { it.id == -1L }))?.cook =
                    cookieManager.getCookieOrganised(HOST)
                CoroutineScope(Dispatchers.IO).launch { Account.save(c, accounts) }
                gsp.edit { putString(SP_ACCOUNT, id) }
                goTo(Main::class, true); return; }
            if (doClearHistory) {
                b.web.clearHistory()
                doClearHistory = false
            }
        }

        override fun onReceivedError(
            view: WebView?, request: WebResourceRequest?, error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
            if (error?.errorCode != null) onError(error.errorCode)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onReceivedError(
            view: WebView?, errorCode: Int, description: String?, failingUrl: String?
        ) {
            super.onReceivedError(view, errorCode, description, failingUrl)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return
            onError(errorCode)
        }

        private fun onError(errorCode: Int) {
            if (errorCode == ERROR_REDIRECT_LOOP) {
                CookieManager.getInstance().removeAllCookies(null)
                b.web.loadUrl(LOGIN_URL)
                failed(
                    Exception("Removing cookies does not fix it, figure out something else!"),
                    false
                )
            }
        }

        private var improperLoading = 0
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            b.refresher.isRefreshing = false
            if ((url != HOST && !url.startsWith("$HOST?")) || browsePurpose == BROWSE_THE_WEB) return
            try { // Don't remove the explanatory comments
                view.evaluateJavascript(
                    "document.getElementsByTagName('body')[0].innerHTML"
                ) { html -> // returns innerHtml of <body> inside "".
                    if (html == "null") {
                        failed(Exception("evaluateJavascript() returned null!"))
                        return@evaluateJavascript; }
                    try {
                        collect(html)
                    } catch (e: JsonSyntaxException) {
                        failed(e)
                    } catch (e: IllegalStateException) {
                        failed(e) // The page may have failed to load properly.
                    } catch (_: NumberFormatException) {
                        // This happens when you go to, for example, the profiles/hashtags page,
                        // tap on the pretty "Instagram" title in the header, then you go to
                        // another page, e.g. sign up page, then you come back to the same
                        // "instagram.com" page, then you repeat this act once more.
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) throw e else failed(e)
            }
        }

        @Throws(JsonSyntaxException::class, NumberFormatException::class)
        private fun collect(html: String) { // UnicodeUnescaper fucks up!
            PageConfig.findFromHtml(
                html, true, { failed(it) }, null, null
            ) { wrapper ->
                id = cookieManager.getCookieOrganised(HOST)
                    .substringAfter("ds_user_id=")
                    .substringBefore(";").toLong().toString()
                val u = Gson().fromJson(
                    Gson().toJson((wrapper.define["PolarisViewer"]!![1] as Map<*, *>)["data"]),
                    User::class.java
                ) // it does not contain edge_saved_media!
                m.acc = Account(
                    id.toLong(), u.username, u.full_name, u.picture(),
                    cookieManager.getCookieOrganised(HOST),
                    Persistent.now()
                ).apply {
                    accounts.removeAll { it.id == id }
                    accounts.add(this)
                    CoroutineScope(Dispatchers.IO).launch { Account.save(c, accounts) }
                }
                gsp.edit { putString(SP_ACCOUNT, id) }
                goTo(Main::class, true)
                improperLoading = 0
            }
        }

        private fun failed(e: Exception, reload: Boolean = true) {
            if (reload) Delay(3000L) { b.web.reload() }
            if (improperLoading < 4) improperLoading++
            else {
                if (BuildConfig.DEBUG) throw e
                else {
                    Toast.makeText(c, R.string.unknownError, Toast.LENGTH_LONG).show()
                    welcome()
                }
            }
        }
    }

    fun CookieManager.getCookieOrganised(url: String): String {
        val raw = getCookie(url)?.split("; ") ?: return ""
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

    @SuppressLint("MissingSuperCall")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (b.web.canGoBack()) {
            b.web.goBack(); return; }
        if (browsePurpose != null) {
            browsePurpose = null
            b.web.loadUrl("")
            welcome()
            return; }
        moveTaskToBack(true)
        killProcess(myPid())
        exitProcess(0)
    }

    override fun onDestroy() {
        browsePurpose = null
        super.onDestroy()
    }
}
