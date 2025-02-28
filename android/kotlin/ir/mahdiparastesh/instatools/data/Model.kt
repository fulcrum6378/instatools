package ir.mahdiparastesh.instatools.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ir.mahdiparastesh.instatools.util.Queue
import java.util.concurrent.CopyOnWriteArraySet

/** Global view model shared between all BaseActivity instances. */
class Model : ViewModel() {
    var acc: Account? = null
    val queue: Queue<Download> = Queue()
    var downloadHistory: CopyOnWriteArraySet<String>? = null
    var fav: ArrayList<Favourite>? = null

    fun accountSwitched() {
        fav = null
    }

    fun saveQueue(pickle: Pickle) {
        pickle.save(queue.toList())
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
