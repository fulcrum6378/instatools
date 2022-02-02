package ir.mahdiparastesh.instatools.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.viewpager2.widget.ViewPager2
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.list.ListSvd
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Versioned

class Expandable(
    private val c: BaseActivity,
    private val slider: ViewPager2,
    private val root: ViewGroup,
    private val handler: Handler?,
    private val colorBg: Int = c.color(R.color.defBG)
) {
    var zoomed = false
    private var currentAnimator: Animator? = null
    private var startScale: Float? = null
    private var startBounds: RectF? = null
    var thumb: View? = null

    fun expand(post: String) {
        if (thumb == null || zoomed) return
        zoomed = true
        currentAnimator?.cancel()
        Api<Media.MediaWrapperApi>(
            c, Api.Type.POST.url.format(post), Media.MediaWrapperApi::class, handler, cache = true
        ) { wrapper ->
            val med = wrapper.items?.get(0)
            if (med == null) {
                handler?.obtainMessage(PageSvd.HANDLE_EXPANDABLE_ERROR)?.sendToTarget()
                return@Api; }
            @Suppress("UNCHECKED_CAST")
            if (med.carousel_media != null)
                slider.adapter = ListCar(c, med.carousel_media as Array<Versioned>)
            else if (med.image_versions2 != null)
                slider.adapter = ListCar(c, arrayOf(med))

        }
        val startBoundsInt = Rect()
        val finalBoundsInt = Rect()
        val globalOffset = Point()

        thumb?.getGlobalVisibleRect(startBoundsInt)
        root.getGlobalVisibleRect(finalBoundsInt, globalOffset)
        startBoundsInt.offset(-globalOffset.x, -globalOffset.y)
        finalBoundsInt.offset(-globalOffset.x, -globalOffset.y)

        startBounds = RectF(startBoundsInt)
        val finalBounds = RectF(finalBoundsInt)

        if ((finalBounds.width() / finalBounds.height() > startBounds!!.width() / startBounds!!.height())) {
            startScale = startBounds!!.height() / finalBounds.height()
            val startWidth: Float = startScale!! * finalBounds.width()
            val deltaWidth: Float = (startWidth - startBounds!!.width()) / 2
            startBounds!!.left -= deltaWidth.toInt()
            startBounds!!.right += deltaWidth.toInt()
        } else {
            startScale = startBounds!!.width() / finalBounds.width()
            val startHeight: Float = startScale!! * finalBounds.height()
            val deltaHeight: Float = (startHeight - startBounds!!.height()) / 2f
            startBounds!!.top -= deltaHeight.toInt()
            startBounds!!.bottom += deltaHeight.toInt()
        }

        thumb!!.alpha = 0f
        slider.visibility = View.VISIBLE
        slider.pivotX = 0f
        slider.pivotY = 0f

        currentAnimator = AnimatorSet().apply {
            play(
                ObjectAnimator.ofFloat(slider, View.X, startBounds!!.left, finalBounds.left)
            ).apply {
                with(
                    ObjectAnimator.ofFloat(slider, View.Y, startBounds!!.top, finalBounds.top)
                )
                with(ObjectAnimator.ofFloat(slider, View.SCALE_X, startScale!!, 1f))
                with(ObjectAnimator.ofFloat(slider, View.SCALE_Y, startScale!!, 1f))
                with(
                    ObjectAnimator.ofArgb(
                        slider, "backgroundColor", c.color(R.color.tp), colorBg
                    )
                )
            }
            duration = ListSvd.zoomDur
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    currentAnimator = null
                }
            })
            start()
        }
        //slider.setOnClickListener { collapse() }
    }

    fun collapse() {
        if (startBounds == null || startScale == null || !zoomed) return
        currentAnimator?.cancel()
        currentAnimator = AnimatorSet().apply {
            play(ObjectAnimator.ofFloat(slider, View.X, startBounds!!.left)).apply {
                with(ObjectAnimator.ofFloat(slider, View.Y, startBounds!!.top))
                with(ObjectAnimator.ofFloat(slider, View.SCALE_X, startScale!!))
                with(ObjectAnimator.ofFloat(slider, View.SCALE_Y, startScale!!))
            }
            duration = ListSvd.zoomDur
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    thumb?.alpha = 1f
                    thumb = null
                    slider.visibility = View.GONE
                    slider.adapter = null
                    currentAnimator = null
                    zoomed = false
                }

                override fun onAnimationCancel(animation: Animator) {
                    thumb?.alpha = 1f
                    thumb = null
                    slider.visibility = View.GONE
                    currentAnimator = null
                    zoomed = false
                }
            })
            start()
        }
    }
}
