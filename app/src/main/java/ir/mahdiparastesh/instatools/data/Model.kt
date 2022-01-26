package ir.mahdiparastesh.instatools.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ir.mahdiparastesh.instatools.json.Profile

class Model : ViewModel() {
    lateinit var acc: Account
    var accounts = arrayListOf<Account>()
    var unfollowers: ArrayList<Unfollower>? = null
    var saved: ArrayList<Profile.Post>? = null
    var dmThread: ArrayList<DmThread>? = null
    var loginLoaded = false
    var nextSaved: Profile.PageInfo? = null

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
