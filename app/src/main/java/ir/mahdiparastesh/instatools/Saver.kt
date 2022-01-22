package ir.mahdiparastesh.instatools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ir.mahdiparastesh.instatools.databinding.SaverBinding

class Saver(val c: Main) : Fragment() {
    private lateinit var b: SaverBinding

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = SaverBinding.inflate(layoutInflater, parent, false)
        return b.root
    }
}
