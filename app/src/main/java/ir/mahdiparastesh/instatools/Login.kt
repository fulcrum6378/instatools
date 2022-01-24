package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.*
import ir.mahdiparastesh.instatools.databinding.LoginBinding
import ir.mahdiparastesh.instatools.more.BaseActivity

class Login : BaseActivity() {
    lateinit var b: LoginBinding
    private val cookieManager: CookieManager = CookieManager.getInstance().also {
        it.setAcceptCookie(true)
    }

    companion object {
        const val host = "https://www.instagram.com/"
        const val spCookies = "cookie_%s"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = LoginBinding.inflate(layoutInflater)
        setContentView(b.root)

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
                    startActivity(Intent(this@Login, Main::class.java))
                    finish()
                } else b.web.loadUrl(req.url.toString())
                return true
            }
        }
    }
}
