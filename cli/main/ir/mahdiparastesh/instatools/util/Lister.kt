package ir.mahdiparastesh.instatools.util

import ir.mahdiparastesh.instatools.InvalidCommandException

abstract class Lister<Item> {
    protected open var list: ArrayList<Item>? = null

    protected open fun fetch() {
        if (list == null) list = arrayListOf()
    }

    protected open fun add(item: Item) {
        list!!.add(item)
    }

    operator fun get(index: String): List<Item> {
        if (list == null) fetch()
        if (list?.isEmpty() == true)
            throw InvalidCommandException("The list is empty!")
        if (index == "all")
            return list!!

        val arr = arrayListOf<Item>()
        try {
            var spd: String
            for (separated in index.split(",")) {
                spd = separated
                if ("-" !in spd)
                    arr.add(list!![spd.toInt() - 1])
                else {
                    val range = spd.split("-")
                    val start = range.first().toIntOrNull()?.let { it - 1 } ?: 0
                    val end = range.last().toIntOrNull() ?: list!!.size
                    for (r in start..<end)
                        arr.add(list!![r])
                }
            }
        } catch (e: Exception) {
            throw InvalidCommandException("The number(s) you entered is incorrect! (${e::class.simpleName})")
        }
        return arr
    }

    abstract class LazyLister<Item> : Lister<Item>() {
        protected var cursor: String? = null
        protected var index: Int = 1

        /** Remember always to call `super.fetch(reset)`. */
        fun fetchSome(reset: Boolean = false) {
            if (reset) {
                cursor = null
                index = 1
            }
            if (cursor == null) list?.clear()
            fetch()
        }

        override fun add(item: Item) {
            super.add(item)
            index++
        }

        protected fun endOfList() {
            cursor = null
            index = 1
            println("End of list.")
        }
    }
}
