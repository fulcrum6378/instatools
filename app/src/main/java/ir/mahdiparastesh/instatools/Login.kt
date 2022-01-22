package ir.mahdiparastesh.instatools

import android.content.Intent
import android.os.Bundle
import ir.mahdiparastesh.instatools.databinding.LoginBinding
import ir.mahdiparastesh.instatools.more.BaseActivity

class Login : BaseActivity() {
    lateinit var b: LoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = LoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        startActivity(Intent(this, Main::class.java))
    }
}
