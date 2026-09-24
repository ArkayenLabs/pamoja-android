package com.pamoja.app.testing

import com.pamoja.app.domain.model.Group
import com.pamoja.app.domain.model.Membership
import com.pamoja.app.domain.model.User
import com.pamoja.app.domain.repository.AuthMethods
import com.pamoja.app.domain.repository.AuthRepository
import com.pamoja.app.domain.repository.GroupRepository
import com.pamoja.app.domain.repository.PhoneVerification
import com.pamoja.app.domain.repository.PhoneVerificationPurpose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class SponsorshipAuthRepository(
    var currentUser: User? = User(userId = "member-1", name = "Asha"),
) : AuthRepository {
    var currentUserReads: Int = 0

    override suspend fun getCurrentUser(): User? {
        currentUserReads += 1
        return currentUser
    }

    override suspend fun isUserLoggedIn(): Boolean = currentUser != null
    override suspend fun signOut(): Result<Unit> = unexpected()
    override suspend fun deleteAccount(): Result<Unit> = unexpected()
    override suspend fun requiresRecentLogin(): Boolean = unexpected()
    override suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit> = unexpected()
    override suspend fun reauthenticateWithEmail(password: String): Result<Unit> = unexpected()
    override suspend fun reauthenticateWithPhone(
        verificationId: String,
        code: String,
    ): Result<Unit> = unexpected()
    override suspend fun getAuthMethods(): AuthMethods = unexpected()
    override suspend fun signUpWithEmail(email: String, password: String): Result<User> = unexpected()
    override suspend fun signInWithEmail(email: String, password: String): Result<User> = unexpected()
    override suspend fun sendPasswordReset(email: String): Result<Unit> = unexpected()
    override suspend fun updatePassword(newPassword: String): Result<Unit> = unexpected()
    override suspend fun signInWithGoogle(idToken: String): Result<User> = unexpected()
    override suspend fun startPhoneVerification(
        phoneNumber: String,
        activity: Any,
        purpose: PhoneVerificationPurpose,
    ): Result<PhoneVerification> = unexpected()
    override suspend fun verifyPhoneCode(verificationId: String, code: String): Result<User> =
        unexpected()
    override suspend fun linkGoogle(idToken: String): Result<User> = unexpected()
    override suspend fun linkEmail(email: String, password: String): Result<User> = unexpected()
    override suspend fun linkPhone(verificationId: String, code: String): Result<User> = unexpected()

    private fun unexpected(): Nothing = error("Unexpected auth repository call")
}

class SponsorshipGroupRepository(
    var groupResult: Result<Group>,
    var membershipResult: Result<Membership>,
) : GroupRepository {
    var groupReads: Int = 0
    var membershipReads: Int = 0

    override suspend fun getGroup(groupId: String): Result<Group> {
        groupReads += 1
        return groupResult
    }

    override suspend fun getMembership(userId: String, groupId: String): Result<Membership> {
        membershipReads += 1
        return membershipResult
    }

    override suspend fun createGroup(group: Group): Result<Group> = unexpected()
    override suspend fun updateGroup(group: Group): Result<Unit> = unexpected()
    override suspend fun getGroupByInviteLink(inviteLink: String): Result<Group> = unexpected()
    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> = unexpected()
    override fun getGroupMembers(groupId: String): Flow<List<User>> = emptyFlow()
    override fun getGroupMemberships(groupId: String): Flow<List<Membership>> = emptyFlow()
    override suspend fun publishMyWeeklySteps(
        groupId: String,
        userId: String,
        steps: Long,
        weekStart: String,
        todaySteps: Long,
        todayDate: String,
    ): Result<Unit> = unexpected()
    override fun getUserGroups(userId: String): Flow<List<Group>> = emptyFlow()
    override suspend fun updateMemberCap(groupId: String, cap: Int): Result<Unit> = unexpected()
    override suspend fun updateWeeklyTarget(groupId: String, target: Int): Result<Unit> = unexpected()
    override suspend fun deactivateInviteLink(groupId: String): Result<Unit> = unexpected()
    override suspend fun updateGroupSettings(
        groupId: String,
        name: String,
        weeklyTarget: Int,
        maxMemberCap: Int,
        canMembersEditTarget: Boolean,
        weekStartDay: String,
    ): Result<Unit> = unexpected()
    override suspend fun removeMember(groupId: String, userId: String): Result<Unit> = unexpected()
    override suspend fun deleteGroup(groupId: String): Result<Unit> = unexpected()
    override suspend fun updateGroupPhoto(groupId: String, photoUrl: String): Result<Unit> = unexpected()
    override suspend fun publishWeeklyTotal(
        groupId: String,
        weeklySteps: Long,
        weekStart: String,
    ): Result<Unit> = unexpected()

    private fun unexpected(): Nothing = error("Unexpected group repository call")
}
