package dev.fitiavana.accounting.ui.changelog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.changelog.ChangelogRelease
import dev.fitiavana.accounting.features.changelog.ChangelogRepository

class ChangelogViewModel(repository: ChangelogRepository) : ViewModel() {

    /** Read once: the changelog is a small file bundled in the APK, and it never changes while the app runs. */
    val releases: LiveData<List<ChangelogRelease>> = MutableLiveData(repository.getReleases())
}

class ChangelogViewModelFactory(private val repository: ChangelogRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChangelogViewModel(repository) as T
    }
}
