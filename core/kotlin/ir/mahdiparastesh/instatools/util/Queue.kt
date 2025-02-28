package ir.mahdiparastesh.instatools.util

import ir.mahdiparastesh.instatools.data.Pickle
import java.util.concurrent.CopyOnWriteArrayList

/** A subclass of [CopyOnWriteArrayList] which doesn't allow duplicates */
class Queue<T> : CopyOnWriteArrayList<T>() {
    override fun add(element: T): Boolean {
        return if (!contains(element)) super.add(element) else false
    }

    fun pickle(pickle: Pickle) {
        pickle.save(toList())
    }
}
