package ir.mahdiparastesh.instatools.view

import ir.mahdiparastesh.instatools.more.BaseActivity

@Suppress("unused")
abstract class BaseFoolery {
    protected val spReported = "rtf_reported"

    var playCensor = false
    var iranCensor = false
    var galaxyCensor = false

    abstract fun onLaunch(c: BaseActivity)

    open fun censorText(raw: String): String = raw
}
