package com.pantrypulse.app.data.repo

import com.pantrypulse.app.data.db.AppDatabase
import com.pantrypulse.app.data.db.HouseholdEntity
import com.pantrypulse.app.data.db.LocationEntity
import com.pantrypulse.app.data.db.MembershipEntity
import com.pantrypulse.app.domain.DefaultLocation
import com.pantrypulse.app.domain.HouseholdType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

class HouseholdRepository(private val db: AppDatabase, private val session: SessionStore) {

    companion object {
        const val ROLE_OWNER = "Owner"
        const val ROLE_MEMBER = "Member"
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }

    fun observeForUser(userId: Long) = db.householdDao().observeForUser(userId)
    fun observe(id: Long) = db.householdDao().observe(id)
    fun observeMembers(householdId: Long) = db.householdDao().observeMembers(householdId)

    suspend fun createHousehold(name: String, type: HouseholdType, ownerId: Long): HouseholdEntity = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Household name cannot be empty." }
        var code: String
        do { code = newInviteCode() } while (db.householdDao().findByInviteCode(code) != null)
        val household = HouseholdEntity(name = name.trim(), type = type, inviteCode = code, ownerId = ownerId)
        val id = db.householdDao().insert(household)
        db.householdDao().insertMembership(MembershipEntity(ownerId, id, ROLE_OWNER))
        db.locationDao().insertAll(DefaultLocation.entries.mapIndexed { i, loc ->
            LocationEntity(householdId = id, name = loc.label, iconKey = loc.iconKey, sortOrder = i)
        })
        household.copy(id = id)
    }

    /** Joins a household with its invite code and makes it the active one. */
    suspend fun joinByCode(userId: Long, code: String): HouseholdEntity = withContext(Dispatchers.IO) {
        val household = db.householdDao().findByInviteCode(code.trim())
            ?: throw AuthException("No household found with code ${code.trim().uppercase()}.")
        db.householdDao().insertMembership(MembershipEntity(userId, household.id, ROLE_MEMBER))
        session.switchHousehold(household.id)
        household
    }

    suspend fun addMember(householdId: Long, userId: Long) = withContext(Dispatchers.IO) {
        db.householdDao().insertMembership(MembershipEntity(userId, householdId, ROLE_MEMBER))
    }

    suspend fun leave(userId: Long, householdId: Long) = withContext(Dispatchers.IO) {
        val mine = db.householdDao().householdsForUser(userId)
        if (mine.size <= 1) throw AuthException("You need at least one household. Create or join another first.")
        db.householdDao().deleteMembership(userId, householdId)
        session.switchHousehold(mine.first { it.id != householdId }.id)
    }

    suspend fun rename(householdId: Long, name: String) = withContext(Dispatchers.IO) {
        if (name.isBlank()) throw AuthException("Name cannot be empty.")
        val h = db.householdDao().findById(householdId) ?: return@withContext
        db.householdDao().update(h.copy(name = name.trim()))
    }

    suspend fun switchTo(householdId: Long) = session.switchHousehold(householdId)

    private fun newInviteCode(): String {
        val random = SecureRandom()
        return (1..6).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
    }
}
