package ir.mahdiparastesh.instatools.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

/** Global view model for all Activity instances. */
class Model : ViewModel() {
    var acc: Account? = null
    val queue: CopyOnWriteArrayList<Queued> = CopyOnWriteArrayList()
    var files: CopyOnWriteArraySet<String>? = null
    var fav: ArrayList<Favourite>? = null

    fun accountSwitched() {
        fav = null
    }

    fun findQueued(it: Queued): Int? {
        for (i in queue.indices) if (queue[i].id == it.id) return i
        return null
    }

    /** Shares the ViewModel across different Activity instances. */
    @Suppress("UNCHECKED_CAST")
    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(Model::class.java)) {
                val key = "Model"
                return if (hashMapViewModel.containsKey(key)) getViewModel(key) as T
                else {
                    addViewModel(key, Model())
                    getViewModel(key) as T
                }
            }
            throw IllegalArgumentException("Unknown Model class")
        }

        companion object {
            val hashMapViewModel = HashMap<String, ViewModel>()

            fun addViewModel(key: String, viewModel: ViewModel) =
                hashMapViewModel.put(key, viewModel)

            fun getViewModel(key: String): ViewModel? = hashMapViewModel[key]
        }
    }
}
