package edu.csumb.magicshroom.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csumb.magicshroom.MagicShroomApplication
import edu.csumb.magicshroom.data.api.Role
import edu.csumb.magicshroom.data.api.UserDto
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the admin screen shows. */
data class AdminUiState(
    val users: List<UserDto> = emptyList(),
    val totalUsers: Long = 0,
    val isLoading: Boolean = true,
    val hasMore: Boolean = false,
    /** The signed-in admin's own id; their row cannot be demoted or deleted from here. */
    val currentUserId: Long? = null,
    /** Users with a role change or delete in flight; their controls are disabled. */
    val busyUserIds: Set<Long> = emptySet(),
    /** Loading the list failed; ready to show with a Retry button. */
    val errorMessage: String? = null,
    /** The last role change or delete failed (e.g. "At least one admin must remain."). */
    val actionMessage: String? = null,
)

/**
 * Admin user management: lists users page by page, grants or revokes ADMIN, and deletes users.
 * The server enforces the ADMIN role on every call; a non-admin just sees a 403 message.
 */
class AdminViewModel(private val users: UserRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminUiState())

    /** Current screen state. */
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private var nextPage = 0

    init {
        viewModelScope.launch {
            (users.getMe() as? ApiResult.Success)?.let { me -> _uiState.update { it.copy(currentUserId = me.data.id) } }
        }
        refresh()
    }

    /** Reloads the list from the first page; also used by the Retry button. */
    fun refresh() {
        nextPage = 0
        load(reset = true)
    }

    /** Appends the next page of users. */
    fun loadMore() {
        val state = _uiState.value
        if (!state.isLoading && state.hasMore) load(reset = false)
    }

    /** Grants ([admin] = true) or revokes the ADMIN role for [user]. */
    fun setAdmin(user: UserDto, admin: Boolean) {
        val role = if (admin) Role.ADMIN else Role.USER
        if (user.role == role) return
        runForUser(user.id) {
            when (val result = users.updateRole(user.id, role)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(users = state.users.map { if (it.id == user.id) result.data else it })
                }
                is ApiResult.Failure -> _uiState.update { it.copy(actionMessage = result.userMessage) }
            }
        }
    }

    /** Deletes [user] and all of their data. */
    fun delete(user: UserDto) {
        runForUser(user.id) {
            when (val result = users.deleteUser(user.id)) {
                is ApiResult.Success -> _uiState.update { state ->
                    state.copy(users = state.users.filterNot { it.id == user.id }, totalUsers = state.totalUsers - 1)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(actionMessage = result.userMessage) }
            }
        }
    }

    /** Hides the last action error. */
    fun dismissMessage() = _uiState.update { it.copy(actionMessage = null) }

    private fun load(reset: Boolean) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = users.listUsers(page = nextPage)) {
                is ApiResult.Success -> {
                    val page = result.data.page
                    nextPage = page.number + 1
                    _uiState.update { state ->
                        state.copy(
                            users = if (reset) result.data.content else (state.users + result.data.content).distinctBy { it.id },
                            totalUsers = page.totalElements,
                            hasMore = nextPage < page.totalPages,
                            isLoading = false,
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, errorMessage = result.userMessage) }
            }
        }
    }

    private fun runForUser(userId: Long, action: suspend () -> Unit) {
        _uiState.update { it.copy(busyUserIds = it.busyUserIds + userId, actionMessage = null) }
        viewModelScope.launch {
            try {
                action()
            } finally {
                _uiState.update { it.copy(busyUserIds = it.busyUserIds - userId) }
            }
        }
    }

    companion object {
        /** Creates the ViewModel with the app's [UserRepository]. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MagicShroomApplication
                AdminViewModel(app.container.userRepository)
            }
        }
    }
}
