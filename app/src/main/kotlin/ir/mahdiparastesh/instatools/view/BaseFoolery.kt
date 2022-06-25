@file:Suppress("unused")

package ir.mahdiparastesh.instatools.view

abstract class BaseFoolery {
    var playCensor = false
    var iranCensor = false
    var galaxyCensor = false

    open fun censorText(raw: String): String = raw
}
