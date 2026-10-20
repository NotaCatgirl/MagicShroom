package edu.csumb.magicshroom.data.repository

import edu.csumb.magicshroom.data.api.MagicShroomApi
import edu.csumb.magicshroom.data.api.Role
import edu.csumb.magicshroom.data.api.UserDto
import edu.csumb.magicshroom.data.api.UserPageDto
import edu.csumb.magicshroom.data.api.UserRolePatchDto

/**
 * The signed-in user's account and admin user management. An interface so ViewModels can be
 * tested with a fake. Never throws for HTTP or network failures; see [ApiResult].
 */
interface UserRepository {
    /** The signed-in user, including their role. */
    suspend fun getMe(): ApiResult<UserDto>

    /** One page of users in id order. Admin only. */
    suspend fun listUsers(page: Int = 0, size: Int = 20): ApiResult<UserPageDto>

    /** Grants or revokes the ADMIN role. Admin only; 409 when it would remove the last admin. */
    suspend fun updateRole(userId: Long, role: Role): ApiResult<UserDto>

    /** Deletes a user and all their data. Admin only; 409 for the last admin. */
    suspend fun deleteUser(userId: Long): ApiResult<Unit>
}

/** [UserRepository] backed by the MagicShroom API. */
class ApiUserRepository(private val api: MagicShroomApi) : UserRepository {
    override suspend fun getMe(): ApiResult<UserDto> = apiCall { api.getMe() }

    override suspend fun listUsers(page: Int, size: Int): ApiResult<UserPageDto> =
        apiCall { api.listUsers(page = page, size = size) }

    override suspend fun updateRole(userId: Long, role: Role): ApiResult<UserDto> =
        apiCall { api.updateUserRole(userId, UserRolePatchDto(role)) }

    override suspend fun deleteUser(userId: Long): ApiResult<Unit> = apiCall { api.deleteUser(userId) }
}
