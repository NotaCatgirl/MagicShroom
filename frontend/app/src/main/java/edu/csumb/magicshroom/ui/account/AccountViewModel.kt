package edu.csumb.magicshroom.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csumb.magicshroom.MagicShroomApplication
import edu.csumb.magicshroom.data.api.UserDto
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads the signed-in user's account from `GET /api/v1/users/me`. The role comes from the server,
 * so admin tools are shown only when the database says the user is an admin.
 */
class AccountViewModel(private val users: UserRepository) : ViewModel() {
    private val _me = MutableStateFlow<UserDto?>(null)

    /** The signed-in user per the API; null until loaded, or when the call fails. */
    val me: StateFlow<UserDto?> = _me.asStateFlow()

    init {
        refresh()
    }

    /** Reloads the account, e.g. after an admin changed roles. */
    fun refresh() {
        viewModelScope.launch {
            _me.value = (users.getMe() as? ApiResult.Success)?.data
        }
    }

    companion object {
        /** Creates the ViewModel with the app's [UserRepository]. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MagicShroomApplication
                AccountViewModel(app.container.userRepository)
            }
        }
    }
}
