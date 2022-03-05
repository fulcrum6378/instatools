package ir.mahdiparastesh.instatools.frag

import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.PageTagBinding
import ir.mahdiparastesh.instatools.more.BasePageViewer

class PageTag(c: Viewer) : BasePageViewer(c) {
    private lateinit var b: PageTagBinding

    override val com: PageCompanion = Companion
    override val root: ConstraintLayout get() = b.root
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf()

    override fun bInitialised(): Boolean = ::b.isInitialized

    companion object : PageCompanion()

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageTagBinding.inflate(inf, parent, false)
        return b.root
    }
}
