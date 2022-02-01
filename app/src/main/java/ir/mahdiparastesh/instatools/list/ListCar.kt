package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.databinding.ListCarBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Versioned

class ListCar(
    val c: BaseActivity, private val slides: Array<Versioned>
) : RecyclerView.Adapter<ListCar.ViewHolder>() {
    class ViewHolder(val b: ListCarBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListCarBinding.inflate(c.layoutInflater, parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        Glide.with(c.c).load(slides[i].best()).into(h.b.image)
    }

    override fun getItemCount() = slides.size
}
