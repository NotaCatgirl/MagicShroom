package edu.csumb.magicshroom.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csumb.magicshroom.MagicShroomApplication
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.CardPageDto
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.data.repository.CardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the search screen shows under the search field. */
data class SearchUiState(
    val cards: List<CardDto> = emptyList(),
    val totalCards: Long = 0,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** Set when the first page fails. */
    val errorMessage: String? = null,
    /** Set when a later page fails; already loaded cards remain visible. */
    val loadMoreErrorMessage: String? = null,
)

/**
 * Searches the card catalog by name through `GET /api/v1/cards?name=`. An empty query lists all
 * cards. Searches start once the user pauses typing, so each keystroke is not a network call.
 */
class SearchViewModel(
    private val searchCards: suspend (name: String?, page: Int) -> ApiResult<CardPageDto>,
) : ViewModel() {
    constructor(cardRepository: CardRepository) : this(
        { name, page -> cardRepository.listCards(name = name, page = page) },
    )

    /** Current text in the search field. */
    var query by mutableStateOf("")
        private set

    private val _uiState = MutableStateFlow(SearchUiState())

    /** Results, loading, and error state for the current query. */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var nextPage = 1

    init {
        search(debounce = false)
    }

    /** Updates the query and searches once the user stops typing. */
    fun onQueryChange(newQuery: String) {
        if (newQuery == query) return
        query = newQuery
        search(debounce = true)
    }

    /** Runs the current search again, e.g. from the Retry button. */
    fun retry() = search(debounce = false)

    /** Appends the next page without discarding results already on screen. */
    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading || current.isLoadingMore || loadMoreJob?.isActive == true || !current.hasMore) return
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreErrorMessage = null) }
            val page = nextPage
            val result = searchCards(query.trim().ifEmpty { null }, page)
            currentCoroutineContext().ensureActive()
            when (result) {
                is ApiResult.Success -> {
                    nextPage = page + 1
                    _uiState.update {
                        it.copy(
                            cards = it.cards + result.data.content,
                            totalCards = result.data.page.totalElements,
                            isLoadingMore = false,
                            hasMore = nextPage < result.data.page.totalPages,
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isLoadingMore = false, loadMoreErrorMessage = result.userMessage)
                }
            }
        }
    }

    private fun search(debounce: Boolean) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        nextPage = 1
        _uiState.value = SearchUiState()
        searchJob = viewModelScope.launch {
            if (debounce) delay(DEBOUNCE_MILLIS)
            val name = query.trim().ifEmpty { null }
            val result = searchCards(name, 0)
            currentCoroutineContext().ensureActive()
            _uiState.value = when (result) {
                is ApiResult.Success -> SearchUiState(
                    cards = result.data.content,
                    totalCards = result.data.page.totalElements,
                    isLoading = false,
                    hasMore = result.data.page.totalPages > 1,
                )
                is ApiResult.Failure -> SearchUiState(isLoading = false, errorMessage = result.userMessage)
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 300L

        /** Creates the ViewModel with the app's [CardRepository]. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MagicShroomApplication
                SearchViewModel(app.container.cardRepository)
            }
        }
    }
}
