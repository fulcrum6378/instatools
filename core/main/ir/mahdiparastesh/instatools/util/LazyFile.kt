package ir.mahdiparastesh.instatools.util

import java.io.Closeable

class LazyFile<OS>(private val creator: Creator<OS>) : Closeable where OS : Closeable {
    private var stream: OS? = null

    fun open(): OS {
        if (stream == null) stream = creator.create()
        return stream!!
    }

    override fun close() {
        if (stream == null) return
        stream!!.close()
    }

    fun interface Creator<OS> where OS : Closeable {
        fun create(): OS
    }
}