package ir.mahdiparastesh.instatools.util

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.media3.common.Player
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.view.UiTools.themeColor

/** Subclass of [BasePage], from which all pages of [Main] extend */
abstract class BasePageMain(private val theme: BaseActivity.Theme) : BasePage<Main>() {

    val inflater: LayoutInflater by lazy { c.themeInflater(theme, c.layoutInflater) }
    override val tbShadow: View? by lazy { c.b.tbShadow }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (empty as TextView).compoundDrawables.getOrNull(1)?.colorFilter =
            PorterDuffColorFilter(c.wrapTheme(theme).themeColor(), PorterDuff.Mode.SRC_IN)
    }

    override fun onPause() {
        super.onPause()
        (expandable?.b?.slider?.adapter as ListCar?)?.sessions
            ?.forEach { if (it?.player?.playbackState == Player.STATE_READY) it.player.pause() }
    }
}
