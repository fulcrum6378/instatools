package ir.mahdiparastesh.instatools.data

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.json.Profile

class Model : ViewModel() {
    var acc: Account? = null
    var unfollowers: ArrayList<Unfollower>? = null
    var saved: ArrayList<Profile.Post>? = null
    var dmThreads: ArrayList<Dm.DmThread>? = null
    var dmThread: Dm.DmThread? = null
    var queueds: ArrayList<Queued>? = null
    var nextSaved: Profile.PageInfo? = null
    var loginLoaded = false
    val currentPage = MutableLiveData(0)
    var vwEdges: ArrayList<Profile.Post>? = null
    var vwInfo: Profile.PageInfo? = null
    var vwPic: String? = null

    fun accountSwitched() {
        unfollowers = null
        saved = null
        dmThreads = null
        dmThread = null
        queueds = null
        nextSaved = null
        loginLoaded = false
        currentPage.value = 0
        vwEdges = null
        vwInfo = null
        vwPic = null
    }

    @Suppress("UNCHECKED_CAST")
    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
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
