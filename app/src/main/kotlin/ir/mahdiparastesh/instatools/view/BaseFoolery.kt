@file:Suppress("unused")

package ir.mahdiparastesh.instatools.view

abstract class BaseFoolery {
    protected val spReported = "rtf_reported"

    var playCensor = false
    var iranCensor = false
    var galaxyCensor = false

    open fun censorText(raw: String): String = raw
}
