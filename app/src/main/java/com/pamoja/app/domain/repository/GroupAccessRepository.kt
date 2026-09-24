package com.pamoja.app.domain.repository

import com.pamoja.app.domain.model.GroupAccessSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Read-only projection of server-owned paid group access.
 *
 * Implementations must confirm the caller's own membership before observing
 * access and must stop observing it as soon as that membership disappears.
 * A successful snapshot may contain paid access, Preview access, both, or
 * neither. A failed result means the server could not currently confirm the
 * complete group state.
 */
interface GroupAccessRepository {
    fun observeForCurrentMember(
        groupId: String,
        userId: String,
    ): Flow<Result<GroupAccessSnapshot>>
}
