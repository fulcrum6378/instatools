package ir.mahdiparastesh.instatools.data

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.json.Media.MediaWrapperApi
import ir.mahdiparastesh.instatools.json.Profile

class Model : ViewModel() {
    var acc: Account? = null
    val files = MutableLiveData<MutableSet<String>?>(null)

    // Login
    var loginLoaded = false

    // Main
    var unfollowers = MutableLiveData<ArrayList<Friend>?>(null)
    var saved: Profile.EdgeList? = null
    var dmInbox: Dm.Inbox? = null
    var dmThread: Dm.DmThread? = null
    val currentPage = MutableLiveData(Settings.defSpMainPage)

    // Downloads
    var queueds: ArrayList<Queued>? = null

    // Viewer
    var vwUser: Profile.User? = null
    var vwTagged: MediaWrapperApi? = null
    var vwCurrentPage = MutableLiveData(1)

    // Favourites
    var fav: ArrayList<Favourite>? = null

    // Mass Follower
    var fwb = MutableLiveData<ArrayList<Followable>?>(null)


    fun accountSwitched() {
        loginLoaded = false
        unfollowers.value = null
        saved = null
        dmInbox = null
        dmThread = null
        queueds = null
        vwUser = null
        vwTagged = null
        fav = null
        fwb.value = null
    }

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
