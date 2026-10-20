package edu.csumb.magicshroom.ui.admin

import edu.csumb.magicshroom.data.api.PageMetaDto
import edu.csumb.magicshroom.data.api.Role
import edu.csumb.magicshroom.data.api.UserDto
import edu.csumb.magicshroom.data.api.UserPageDto
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** State transitions of the admin screen against a fake user repository. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val admin = UserDto(1, "admin@example.com", "Admin Shroom", Role.ADMIN)
    private val playerOne = UserDto(2, "player.one@example.com", "Player One", Role.USER)
    private val playerTwo = UserDto(3, "player.two@example.com", "Player Two", Role.USER)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsUsersAndRemembersWhoIsSignedIn() = runTest(dispatcher) {
        val viewModel = AdminViewModel(FakeUsers(listOf(admin, playerOne, playerTwo)))

        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L, 3L), state.users.map { it.id })
        assertEquals(3L, state.totalUsers)
        assertEquals(1L, state.currentUserId)
        assertFalse(state.isLoading)
    }

    @Test
    fun grantingAdminUpdatesTheRow() = runTest(dispatcher) {
        val viewModel = AdminViewModel(FakeUsers(listOf(admin, playerOne)))
        runCurrent()

        viewModel.setAdmin(playerOne, admin = true)
        runCurrent()

        assertEquals(Role.ADMIN, viewModel.uiState.value.users.single { it.id == 2L }.role)
        assertTrue(viewModel.uiState.value.busyUserIds.isEmpty())
    }

    @Test
    fun rejectedChangeKeepsTheRoleAndShowsTheServerMessage() = runTest(dispatcher) {
        val users = FakeUsers(listOf(admin, playerOne)).apply {
            roleResult = ApiResult.HttpError(409, "At least one admin must remain.")
        }
        val viewModel = AdminViewModel(users)
        runCurrent()

        viewModel.setAdmin(admin, admin = false)
        runCurrent()

        assertEquals(Role.ADMIN, viewModel.uiState.value.users.single { it.id == 1L }.role)
        assertEquals("At least one admin must remain.", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun deletingRemovesTheUserFromTheTable() = runTest(dispatcher) {
        val viewModel = AdminViewModel(FakeUsers(listOf(admin, playerOne, playerTwo)))
        runCurrent()

        viewModel.delete(playerTwo)
        runCurrent()

        assertEquals(listOf(1L, 2L), viewModel.uiState.value.users.map { it.id })
        assertEquals(2L, viewModel.uiState.value.totalUsers)
    }

    @Test
    fun nonAdminSeesTheForbiddenMessageInsteadOfAList() = runTest(dispatcher) {
        val users = FakeUsers(emptyList()).apply { listResult = ApiResult.HttpError(403, null) }
        val viewModel = AdminViewModel(users)

        runCurrent()

        assertTrue(viewModel.uiState.value.users.isEmpty())
        assertEquals("You don't have permission to do that.", viewModel.uiState.value.errorMessage)
    }

    /** In-memory [UserRepository]; the signed-in user is the first in [initial]. */
    private class FakeUsers(initial: List<UserDto>) : UserRepository {
        private val users = initial.toMutableList()
        var listResult: ApiResult<UserPageDto>? = null
        var roleResult: ApiResult<UserDto>? = null

        override suspend fun getMe(): ApiResult<UserDto> =
            users.firstOrNull()?.let { ApiResult.Success(it) } ?: ApiResult.HttpError(401, null)

        override suspend fun listUsers(page: Int, size: Int): ApiResult<UserPageDto> = listResult
            ?: ApiResult.Success(UserPageDto(users.toList(), PageMetaDto(size, page, users.size.toLong(), 1)))

        override suspend fun updateRole(userId: Long, role: Role): ApiResult<UserDto> = roleResult ?: run {
            val index = users.indexOfFirst { it.id == userId }
            users[index] = users[index].copy(role = role)
            ApiResult.Success(users[index])
        }

        override suspend fun deleteUser(userId: Long): ApiResult<Unit> {
            users.removeAll { it.id == userId }
            return ApiResult.Success(Unit)
        }
    }
}
