package edu.csumb.magicshroom.api.dto

import edu.csumb.magicshroom.api.model.Role

/** Body of PATCH /api/v1/users/{userId} (`UserPatch`): the new role, the only editable field. */
data class UserPatch(val role: Role)
