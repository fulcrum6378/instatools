package ir.mahdiparastesh.instatools.util

import java.util.concurrent.CopyOnWriteArrayList

/** A subclass of [CopyOnWriteArrayList] which doesn't allow duplicates */
class Queue<T> : CopyOnWriteArrayList<T>() where T : Queue.Item {
    override fun add(element: T): Boolean {
        return if (!contains(element)) super.add(element) else false
    }

    /** A structure for a single item of a [Queue] */
    interface Item {
        /** A unique ID */
        val id: String

        /** 0=>pending, 1=>failed */
        var status: Byte

        /* A lazy field fo file name */
        val fileName: String


        fun ready() = status == 0.toByte()

        fun isFailed() = status == 1.toByte()
    }
}
