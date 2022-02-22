package ir.mahdiparastesh.instatools

import android.content.Intent
import android.os.Bundle
import androidx.annotation.MainThread
import ir.mahdiparastesh.instatools.databinding.MassFollowerBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.serv.Follower

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

    companion object {
        @MainThread
        fun initService(c: BaseActivity, enq: Follower.ToBeEnqueued) {
            c.startService(Intent(c, Follower::class.java).apply {
                putExtra(Follower.EXTRA_ENQUEUE, enq)
            })
        }
    }
}
