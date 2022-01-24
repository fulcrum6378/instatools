package ir.mahdiparastesh.instatools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ir.mahdiparastesh.instatools.databinding.DirectBinding
import ir.mahdiparastesh.instatools.more.BaseActivity

class Direct(val c: Main) : Fragment() {
    private lateinit var b: DirectBinding

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = DirectBinding.inflate(
            c.themeInflator(BaseActivity.Theme.TERTIARY, inf),
            parent, false
        )
        return b.root
    }
}
