package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.databinding.LoginBinding
import ir.mahdiparastesh.instatools.databinding.WelcomeBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.UiTools.Companion.vis
import java.io.FileOutputStream
import java.util.*

// adb connect 192.168.1.20:

class Login : BaseActivity() {
    private lateinit var b: LoginBinding
    private lateinit var bw: WelcomeBinding
    private lateinit var cookieManager: CookieManager
    private var saveLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.data?.data == null) return@registerForActivityResult
            val uri = it.data!!.data!!
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sp.edit().apply {
                putString(spStorage, uri.toString())
                apply()
            }
            files()
        }

    companion object {
        const val host = "https://www.instagram.com/"
        const val spCookies = "cookie_%s"
        const val spAccount = "account"
        const val spStorage = "storage"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = LoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.welcomeStub.setOnInflateListener { _, v -> bw = WelcomeBinding.bind(v) }

        when (val acc = sp.getString(spAccount, "")) {
            "" -> welcome()
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
                } else welcome()
            }
        }
    }

    private fun welcome() {
        b.welcomeStub.inflate()
        if (!sp.contains(spStorage)) saveLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            //putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }) else files()

        // TODO: SHOW WELCOME WITH SKIP + ACCOUNTS OPTIONS
        // WHEN A USER LOGS OUT, THE "spAccount" WILL BE REMOVED!
        // BUT THIS DOESN'T HAPPEN WHEN HE/SHE SWITCHES ACCOUNTS!
    }

    private fun files() {
        val tree = DocumentFile.fromTreeUri(c, Uri.parse(sp.getString(spStorage, null)))!!
        val newbie = "InstaTools"
        val oldie = "1.txt"
        var sub = tree.findFile(newbie)
        if (sub == null) sub = tree.createDirectory(newbie)
        var file = sub!!.findFile(oldie)
        if (file == null) file = sub.createFile("text/plain", "1.txt")
        c.contentResolver.openFileDescriptor(file!!.uri, "wa")?.use { des ->
            FileOutputStream(des.fileDescriptor).use { fos ->
                fos.write("${Calendar.getInstance().timeInMillis}: Kun\n".encodeToByteArray())
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun browse() {
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
