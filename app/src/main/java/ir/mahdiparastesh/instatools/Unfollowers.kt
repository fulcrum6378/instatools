package ir.mahdiparastesh.instatools

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ir.mahdiparastesh.instatools.databinding.UnfollowersBinding

class Unfollowers(val c: Main) : Fragment() {
    private lateinit var b: UnfollowersBinding

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = UnfollowersBinding.inflate(layoutInflater, parent, false)
        return b.root
    }
}
