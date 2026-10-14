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
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.data.repository.CardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Set when the last search failed; ready to show on screen. */
    val errorMessage: String? = null,
)

// TODO(test): unit-test the debounce and the error state with a fake CardRepository.
/**
 * Searches the card catalog by name through `GET /api/v1/cards?name=`. An empty query lists all
 * cards. Searches start once the user pauses typing, so each keystroke is not a network call.
 */
class SearchViewModel(private val cardRepository: CardRepository) : ViewModel() {

    /** Current text in the search field. */
    var query by mutableStateOf("")
        private set

    private val _uiState = MutableStateFlow(SearchUiState())

    /** Results, loading, and error state for the current query. */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        search(debounce = false)
    }

    /** Updates the query and searches once the user stops typing. */
    fun onQueryChange(newQuery: String) {
        query = newQuery
        search(debounce = true)
    }

    /** Runs the current search again, e.g. from the Retry button. */
    fun retry() = search(debounce = false)

    private fun search(debounce: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(DEBOUNCE_MILLIS)
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val name = query.trim().ifEmpty { null }
            // TODO(api): only the first page (20 cards) is fetched; add "load more" using page/size.
            _uiState.value = when (val result = cardRepository.listCards(name = name)) {
                is ApiResult.Success -> SearchUiState(
                    cards = result.data.content,
                    totalCards = result.data.page.totalElements,
                    isLoading = false,
                )
                is ApiResult.Failure -> _uiState.value.copy(isLoading = false, errorMessage = result.userMessage)
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
