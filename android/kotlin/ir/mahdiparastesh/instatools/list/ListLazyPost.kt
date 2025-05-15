package ir.mahdiparastesh.instatools.list

import android.widget.ImageView
import android.widget.Toast
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.base.BasePage
import ir.mahdiparastesh.instatools.base.SelectiveActivity
import ir.mahdiparastesh.instatools.job.SimpleJobs
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Subclass of [ListPost] that loads each [Media] model directly from the Instagram [Api]
 * before passing them to [Expandable].
 */
abstract class ListLazyPost<Activity, Fragment>(c: Activity, f: Fragment) :
    ListPost<Activity, Fragment>(c, f)
    where Activity : SelectiveActivity, Fragment : BasePage<Activity> {

    private var loadingMedia = false

    override fun expand(v: ImageView, i: Int) {
        val med = this[i] ?: return
        if (med.taken_at != null) {
            super.expand(v, i)
            return
        }

        // load the original post if it hasn't been loaded yet
        if (loadingMedia) return
        val shortCode = med.code ?: return
        loadingMedia = true
        CoroutineScope(Dispatchers.IO).launch {
            var error: Api.FailureException? = null
            try {
                this@ListLazyPost[i] = SimpleJobs.handlePostLink(Utils.POST_LINK.format(shortCode))
            } catch (e: Api.FailureException) {
                error = e
            }

            withContext(Dispatchers.Main) {
                if (error == null)
                    super.expand(v, i)
                else
                // UiTools.snackbar(b.root, UiTools.apiError(c, e.code))
                    Toast.makeText(c, UiTools.apiError(c.c, error.code), Toast.LENGTH_LONG).show()
                loadingMedia = false
            }
        }
    }

    abstract operator fun set(position: Int, item: Media)
}
