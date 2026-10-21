package edu.csumb.magicshroom.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csumb.magicshroom.MagicShroomApplication
import edu.csumb.magicshroom.data.api.DeckCardPatchDto
import edu.csumb.magicshroom.data.api.DeckSection
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.data.repository.DeckRepository
import edu.csumb.magicshroom.data.repository.ResolvedDeck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeckListUiState(
    val decks: List<ResolvedDeck> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
)

/** Loads and mutates the signed-in user's deck collection. */
class DeckListViewModel(private val decks: DeckRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DeckListUiState())
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    init { refresh() }

    /** Reloads the signed-in user's decks and their card records. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = decks.listMyDecksWithCards()) {
                is ApiResult.Success -> _uiState.value = DeckListUiState(decks = result.data, isLoading = false)
                is ApiResult.Failure -> _uiState.value = DeckListUiState(isLoading = false, errorMessage = result.userMessage)
            }
        }
    }

    /** Creates a deck, then reloads the collection. */
    fun create(name: String) = mutate {
        when (val result = decks.createDeck(name)) {
            is ApiResult.Success -> refresh()
            is ApiResult.Failure -> showError(result.userMessage)
        }
    }

    /** Deletes a deck, then reloads the collection. */
    fun delete(deckId: Long) = mutate {
        when (val result = decks.deleteDeck(deckId)) {
            is ApiResult.Success -> refresh()
            is ApiResult.Failure -> showError(result.userMessage)
        }
    }

    /** Runs a collection mutation and updates the working state. */
    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, errorMessage = null)
            block()
            _uiState.value = _uiState.value.copy(isWorking = false)
        }
    }

    /** Stores an operation error for presentation by the screen. */
    private fun showError(message: String) { _uiState.value = _uiState.value.copy(errorMessage = message) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MagicShroomApplication
                DeckListViewModel(app.container.deckRepository)
            }
        }
    }
}

data class DeckDetailUiState(
    val deck: ResolvedDeck? = null,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
)

/** Loads and mutates a deck and its entries. */
class DeckDetailViewModel(private val decks: DeckRepository, private val deckId: Long) : ViewModel() {
    private val _uiState = MutableStateFlow(DeckDetailUiState())
    val uiState: StateFlow<DeckDetailUiState> = _uiState.asStateFlow()

    init { refresh() }

    /** Reloads the selected deck and resolves its card records. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = decks.getMyDeckWithCards(deckId)) {
                is ApiResult.Success -> _uiState.value = DeckDetailUiState(deck = result.data, isLoading = false)
                is ApiResult.Failure -> _uiState.value = DeckDetailUiState(isLoading = false, errorMessage = result.userMessage)
            }
        }
    }

    /** Renames this deck and reloads its contents. */
    fun rename(name: String) = mutate { decks.renameDeck(deckId, name) }

    /** Adds a catalog card to this deck and reloads its contents. */
    fun addCard(cardId: Long, quantity: Int, section: DeckSection) = mutate {
        decks.addCard(deckId, cardId, quantity, section)
    }

    /** Updates a deck card's quantity and section. */
    fun updateCard(cardId: Long, quantity: Int, section: DeckSection) = mutate {
        decks.updateCard(deckId, cardId, DeckCardPatchDto(quantity = quantity, section = section))
    }

    /** Removes a card from this deck and reloads its contents. */
    fun removeCard(cardId: Long) = mutate { decks.removeCard(deckId, cardId) }

    /** Runs a deck operation and refreshes state after success. */
    private fun mutate(action: suspend () -> ApiResult<*>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, errorMessage = null)
            when (val result = action()) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    errorMessage = result.userMessage,
                )
            }
        }
    }

    companion object {
        /** Creates a ViewModel for the requested deck using the app's shared repository. */
        fun factory(deckId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MagicShroomApplication
                DeckDetailViewModel(app.container.deckRepository, deckId)
            }
        }
    }
}
