package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Process.killProcess
import android.os.Process.myPid
import android.view.View
import android.view.ViewStub
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import ir.mahdiparastesh.instatools.base.BaseActivity
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.databinding.LoginBinding
import ir.mahdiparastesh.instatools.databinding.WelcomeBinding
import ir.mahdiparastesh.instatools.job.SimpleJobs
import ir.mahdiparastesh.instatools.list.ListAcc
import ir.mahdiparastesh.instatools.util.Delay
import ir.mahdiparastesh.instatools.util.StorageManager
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.text.StringEscapeUtils
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.system.exitProcess

/**
 * Lists and controls all [Account]s and opens a [WebView] for signing in to Instagram
 * and storing cookies. Also lets the user freely browse Instagram using their own account.
 *
 * Note: Going to the Instagram Accounts Centre inside a WebView will drop you out of login;
 * it doesn't erase cookies.
 */
class Login : BaseActivity(), ViewStub.OnInflateListener {
    private lateinit var b: LoginBinding
    private lateinit var bw: WelcomeBinding
    lateinit var accounts: ArrayList<Account>
    private lateinit var cookieManager: CookieManager
    var accBrowsingWeb: Account? = null
    var injectingCookieForAcc: Long? = null
    private var uploadCallback: ValueCallback<Array<out Uri?>?>? = null

    override val menuRes: Int? = null

    companion object {
        const val IG_HOST = "www.instagram.com"
        const val IG_HOST_NO_WWW = "instagram.com"
        const val IG_HOME = "https://$IG_HOST/"
        const val IG_HOME_NO_WWW = "https://instagram.com/"
        const val IG_LOGIN_PAGE = "${IG_HOME}accounts/login/"
        const val SP_ACCOUNT = "account"  // String
        const val EXTRA_NEED_AUTH = "needAuthentication"
        const val BROWSE_FOR_ADD = 0
        const val BROWSE_ACC_EXIST = 1
        const val BROWSE_AUTH_REQ = 2
        const val BROWSE_THE_WEB = 3

        /** Remember to set this to null when you're finished. */
        var browsePurpose: Int? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = LoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.welcomeStub.setOnInflateListener(this)

        // WebView
        b.web.settings.apply {
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/136.0.0.0 Safari/537.36"
            // check https://www.whatismybrowser.com/guides/the-latest-user-agent/chrome
            javaScriptEnabled = true
            domStorageEnabled = true

            // for file upload:
            allowFileAccess = true
        }
        b.web.webViewClient = webViewClient
        b.web.webChromeClient = webChromeClient
        if (night) b.web.isForceDarkAllowed = true
        b.refresher.setOnRefreshListener { b.web.reload() }

        // what to show?
        accounts = Account.load(c)
        when {
            browsePurpose != null -> {  // on configuration changed while browsing
                b.refresher.vis()
                if (::bw.isInitialized) bw.root.vis(false)
            }
            intent.hasExtra(EXTRA_NEED_AUTH) -> intent.getLongExtra(
                EXTRA_NEED_AUTH, -1L
            ).apply {
                AlertDialog.Builder(this@Login).apply {
                    setTitle(R.string.loggedOut)
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

    override fun onInflate(stub: ViewStub, v: View) {
        bw = WelcomeBinding.bind(v)
        if (night) bw.logo.drawable.setTint(color(R.color.defCA))
        accounts.sortByDescending { it.last_used.toString() }
        accounts.sortBy { it.id < 0L }
        bw.accounts.adapter = ListAcc(this)

        // add Account
        bw.addAccount.setOnClickListener { browse(BROWSE_FOR_ADD) }
    }

    private fun welcome() {
        b.refresher.vis(false)
        if (!::bw.isInitialized) b.welcomeStub.inflate()
        else bw.root.vis()
    }

    val injectCookies = launcherForResult {
        val acc = injectingCookieForAcc?.let { id -> accounts.find { a -> a.id == id } }
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
                        c, R.string.cookieInjectionSuccess,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        c, R.string.importReadError,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        injectingCookieForAcc = null
    }
    val exportCookies = launcherForResult {
        val acc = injectingCookieForAcc?.let { id -> accounts.find { a -> a.id == id } }
            ?: return@launcherForResult
        if (it.resultCode == RESULT_OK) CoroutineScope(Dispatchers.IO).launch {
            contentResolver.openFileDescriptor(it.data!!.data!!, "w")!!.use { des ->
                FileOutputStream(des.fileDescriptor).use { fos ->
                    fos.write((acc.cook ?: "").encodeToByteArray())
                }
            }
        }
        injectingCookieForAcc = null
    }

    fun selectAccount(acc: Account) {
        when {
            acc.cook != null -> browse(BROWSE_ACC_EXIST, acc.cook)
            else -> {
                accounts.removeAll { it.id == acc.id }
                CoroutineScope(Dispatchers.IO).launch { Account.save(c, accounts) }
                welcome()
            }
        }
    }

    private var doClearHistory = false
    fun browse(purpose: Int, withCookie: String? = "", beginWith: String = IG_LOGIN_PAGE) {
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
                    cm.setCookie(IG_HOME, settable!![i]) {
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

    private val webViewClient = object : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            b.refresher.isRefreshing = true
            if (doClearHistory) {
                b.web.clearHistory()
                doClearHistory = false
            }
        }

        override fun onReceivedError(
            view: WebView?, request: WebResourceRequest?, error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            if (error?.errorCode != null) onError(error.errorCode)
        }

        private fun onError(errorCode: Int) {
            if (errorCode == ERROR_REDIRECT_LOOP) {
                CookieManager.getInstance().removeAllCookies(null)
                b.web.loadUrl(IG_LOGIN_PAGE)
                failed(
                    Exception(
                        "Removing cookies does not fix it, figure out something else!"
                    ),
                    false
                )
            }
        }

        private var improperLoading = 0
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            b.refresher.isRefreshing = false
            if (url != IG_HOME && !url.startsWith("$IG_HOME?")
                && !url.startsWith("$IG_HOME#")
            ) return

            if (browsePurpose != BROWSE_THE_WEB) try {
                view.evaluateJavascript(
                    "document.getElementsByTagName('body')[0].innerHTML"
                ) { html ->
                    if (html == "null") {
                        failed(Exception("evaluateJavascript() returned null!"))
                        return@evaluateJavascript; }
                    try {
                        collect(html)
                    } catch (e: IllegalStateException) {
                        failed(e)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) throw e else failed(e)
            }
            // the user is just browsing the web
            else accBrowsingWeb?.also { accBrowsingWeb ->
                accBrowsingWeb.justAuthenticated()
                accBrowsingWeb.cook = cookieManager.getCookieOrganised(IG_HOME)
                accBrowsingWeb.saveMeInIO(c)
            }
        }

        @Throws(IllegalStateException::class)
        private fun collect(html: String) {
            val u = SimpleJobs.userFromHtml(
                StringEscapeUtils.unescapeJson(html)  // UnicodeUnescaper fucks up!
            ) ?: throw IllegalStateException("Couldn't extract user information!")

            val id = u.id().toLong()
            c.acc = Account(
                id, u.username, u.full_name, u.originalPicture(),
                cookieManager.getCookieOrganised(IG_HOME),
            ).apply {
                accounts.removeAll { it.id == id }
                accounts.add(this)
                CoroutineScope(Dispatchers.IO).launch { Account.save(c, accounts) }
                c.onLoggedIn(this, true)
            }

            goTo(Main::class, true)
            improperLoading = 0
            browsePurpose = null

            CoroutineScope(Dispatchers.IO).launch {
                c.storageManager.clearCacheIfNecessary(StorageManager.CacheSubdir.WEB)
                c.storageManager.deleteExpiredCachePickles(id)
            }
        }

        private fun failed(e: Exception, reload: Boolean = true) {
            if (reload) Delay(3000L) { b.web.reload() }
            if (improperLoading < 4) improperLoading++
            else {
                if (BuildConfig.DEBUG) throw e
                else {
                    Toast.makeText(
                        c, R.string.couldNotLogin,
                        Toast.LENGTH_LONG
                    ).show()
                    welcome()
                }
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            if (request.url.host == IG_HOST || request.url.host == IG_HOST_NO_WWW)
                false
            else {
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                true
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

    private val webChromeClient = object : WebChromeClient() {

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<out Uri?>?>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            uploadCallback = filePathCallback
            val intent = fileChooserParams?.createIntent()
            if (intent != null) {
                fileChooserLauncher.launch(intent)
                return true
            }
            return false
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (uploadCallback == null) return@registerForActivityResult

        var results: Array<Uri>? = null
        if (result.resultCode == RESULT_OK) result.data?.also { intent ->
            if (intent.dataString != null)
                results = arrayOf(intent.dataString!!.toUri())
            else intent.clipData?.also { clipData ->
                val uriList = mutableListOf<Uri>()
                for (i in 0 until clipData.itemCount)
                    uriList.add(clipData.getItemAt(i).uri)
                results = uriList.toTypedArray()
            }
        }
        uploadCallback?.onReceiveValue(results)
        uploadCallback = null
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        b.web.invalidate()
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
}
