package ir.mahdiparastesh.instatools

import android.os.Bundle
import ir.mahdiparastesh.instatools.databinding.MassFollowerBinding
import ir.mahdiparastesh.instatools.more.BaseActivity

class MassFollower : BaseActivity() {
    private lateinit var b: MassFollowerBinding
    override val menuRes: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = MassFollowerBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.massFollower)

        // Guide
        arrayOf(b.guideTv1, b.guideTv2, b.guideTv3)
            .forEach { it.typeface = fontRegular }
        b.guideIv.setOnClickListener {
        }
    }
}
