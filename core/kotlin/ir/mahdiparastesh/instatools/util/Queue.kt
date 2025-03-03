package ir.mahdiparastesh.instatools.util

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.data.Pickle
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A wrapper for [CopyOnWriteArrayList] which doesn't allow duplicates
 * and regularly updates its own [Pickle].
 */
@Suppress("UNCHECKED_CAST")
class Queue<T>(val pickle: Pickle?) {
    var list: CopyOnWriteArrayList<T>? = null

    inline fun <reified R> list(): CopyOnWriteArrayList<R> {
        if (list != null) return list as CopyOnWriteArrayList<R>
        return (pickle?.restore<List<R>>()?.let { CopyOnWriteArrayList(it) }
            ?: CopyOnWriteArrayList<R>())
            .also { list = it as CopyOnWriteArrayList<T> }
    }

    inline fun <reified R> size(): Int = list<R>().size
    inline fun <reified R> isEmpty(): Boolean = list<R>().isEmpty()
    inline fun <reified R> iterator(): Iterator<T> =
        (list<R>() as CopyOnWriteArrayList<T>).iterator()

    inline fun <reified R> getOrNull(index: Int): T? {
        val list = list<R>() as CopyOnWriteArrayList<T>
        return if (list.size > index) list[index] else null
    }

    inline fun <reified R> indexOf(element: T): Int {
        val list = list<R>() as CopyOnWriteArrayList<T>
        return list.indexOf(element)
    }

    inline fun <reified R> add(element: T, autoSave: Boolean) {
        val list = list<R>() as CopyOnWriteArrayList<T>
        if (!list.contains(element))
            list.add(element)
        if (autoSave) save<R>()
    }

    inline fun <reified R> addAll(elements: Collection<T>, autoSave: Boolean) {
        val list = list<R>() as CopyOnWriteArrayList<T>
        for (element in elements)
            if (!list.contains(element))
                list.add(element)
        if (autoSave) save<R>()
    }

    inline fun <reified R> remove(element: T) {
        val list = list<R>() as CopyOnWriteArrayList<T>
        list.remove(element)
        save<R>()
    }

    inline fun <reified R> removeAt(index: Int) {
        list<R>().removeAt(index)
        save<R>()
    }

    inline fun <reified R> clear() {
        list<R>().clear()
        save<R>()
    }

    inline fun <reified R> save() {
        pickle?.save<List<R>>(list<R>().toList())
    }

    inline fun <reified R> export(): String {
        return Api.json.encodeToString(list<R>().toList())
    }

    inline fun <reified R> import(json: String) {
        addAll<R>(Api.json.decodeFromString<List<R>>(json) as List<T>, true)
    }
}
