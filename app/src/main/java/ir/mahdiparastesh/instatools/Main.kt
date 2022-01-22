package ir.mahdiparastesh.instatools

import android.os.Bundle
import ir.mahdiparastesh.instatools.databinding.MainBinding
import ir.mahdiparastesh.instatools.more.BaseActivity

// adb connect 192.168.1.20:

class Main : BaseActivity() {
    lateinit var b: MainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = MainBinding.inflate(layoutInflater)
        setContentView(b.root)
    }
}
