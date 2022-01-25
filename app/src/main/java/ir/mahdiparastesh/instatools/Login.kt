package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.webkit.*
import ir.mahdiparastesh.instatools.databinding.LoginBinding
import ir.mahdiparastesh.instatools.databinding.WelcomeBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.UiTools.Companion.vis
import java.util.*

// adb connect 192.168.1.20:

class Login : BaseActivity(), ViewStub.OnInflateListener {
    private lateinit var b: LoginBinding
    private lateinit var bw: WelcomeBinding
    private lateinit var cookieManager: CookieManager

    companion object {
        const val host = "https://www.instagram.com/"
        const val spCookies = "cookie_%s"
        const val spAccount = "account"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = LoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        when (val acc = sp.getString(spAccount, "")) {
            "" -> b.welcomeStub.inflate()
            null -> {
                m.id = null
                goAhead()
            }
            else -> (sp.getString(spCookies.format(acc), null)).apply {
                if (this != null) {
                    cookieManager = CookieManager.getInstance().also {
                        it.setAcceptCookie(true)
                        it.setCookie(host, this)
                    }
                    browse()
                } else {
                    sp.edit().apply {
                        remove(spCookies.format(acc))
                        apply()
                    }
                    b.welcomeStub.inflate()
                }
            }
        }

        // Welcome
        b.welcomeStub.setOnInflateListener(this)
    }

    override fun onInflate(stub: ViewStub, inflated: View) {
        bw = WelcomeBinding.bind(inflated)

        // TODO: SHOW WELCOME WITH SKIP + ACCOUNTS OPTIONS
        // WHEN A USER LOGS OUT, THE "spAccount" WILL BE REMOVED!
        // BUT THIS DOESN'T HAPPEN WHEN HE/SHE SWITCHES ACCOUNTS!
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun browse() {
        setContentView(b.root)
        vis(b.web)
        b.web.settings.javaScriptEnabled = true
        b.web.loadUrl("${host}accounts/login/")
        b.web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                if (req == null) return false
                if (req.url.toString() == host) {
                    m.id = cookieManager.getCookie(host)
                        .substringAfter("ds_user_id=")
                        .substringBefore(";")
                    sp.edit().apply {
                        putString(spCookies.format(m.id), cookieManager.getCookie(host))
                        apply()
                    }
                    goAhead()
                } else b.web.loadUrl(req.url.toString())
                return true
            }
        }
    }

    private fun goAhead() {
        startActivity(Intent(this@Login, Main::class.java))
        finish()
    }
}
