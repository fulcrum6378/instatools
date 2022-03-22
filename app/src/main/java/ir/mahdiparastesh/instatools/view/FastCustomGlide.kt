package ir.mahdiparastesh.instatools.view

import android.graphics.drawable.Drawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class FastCustomGlide(private val onLoaded: (drw: Drawable) -> Unit) : CustomTarget<Drawable>() {
    override fun onLoadCleared(placeholder: Drawable?) {}
    override fun onResourceReady(
        res: Drawable, trans: Transition<in Drawable>?
    ) {
        onLoaded(res)
    }
}
