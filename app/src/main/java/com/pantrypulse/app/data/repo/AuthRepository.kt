package com.pantrypulse.app.data.repo

import com.pantrypulse.app.data.db.AppDatabase
import com.pantrypulse.app.data.db.UserEntity
import com.pantrypulse.app.domain.HouseholdType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthException(message: String) : Exception(message)

class AuthRepository(
    private val db: AppDatabase,
    private val session: SessionStore,
    private val households: HouseholdRepository,
) {
    companion object {
        private val emailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
        val avatarColors = listOf(
            0xFF1F7A57, 0xFFF2994A, 0xFF2D9CDB, 0xFF9B51E0, 0xFFEB5757, 0xFF27AE60, 0xFFF2C94C, 0xFF56CCF2,
        ).map { it.toInt() }

        fun validate(name: String?, email: String, password: String) {
            if (name != null && name.isBlank()) throw AuthException("Please enter your name.")
            if (!emailRegex.matches(email.trim())) throw AuthException("Please enter a valid email address.")
            if (password.length < 6) throw AuthException("Password must be at least 6 characters.")
        }
    }

    /** Creates a profile, a personal household for it, and signs it in. */
    suspend fun signUp(name: String, email: String, password: String): UserEntity = withContext(Dispatchers.IO) {
        val user = createProfile(name, email, password)
        val household = households.createHousehold("${user.displayName.substringBefore(' ')}'s Home", HouseholdType.PERSONAL, user.id)
        session.signIn(user.id, household.id)
        user
    }

    /** Creates a profile without signing in (used to add family members or colleagues). */
    suspend fun createProfile(name: String, email: String, password: String): UserEntity = withContext(Dispatchers.IO) {
        validate(name, email, password)
        val cleanEmail = email.trim().lowercase()
        if (db.userDao().findByEmail(cleanEmail) != null) throw AuthException("An account with this email already exists.")
        val salt = PasswordHasher.newSalt()
        val user = UserEntity(
            displayName = name.trim(),
            email = cleanEmail,
            passwordHash = PasswordHasher.hash(password, salt),
            salt = salt,
            avatarColor = avatarColors[(cleanEmail.hashCode() and 0x7fffffff) % avatarColors.size],
        )
        user.copy(id = db.userDao().insert(user))
    }

    suspend fun signIn(email: String, password: String): UserEntity = withContext(Dispatchers.IO) {
        validate(null, email, password)
        val user = db.userDao().findByEmail(email.trim())
            ?: throw AuthException("No account found for this email.")
        if (!PasswordHasher.verify(password, user.salt, user.passwordHash)) throw AuthException("Incorrect password.")
        val previous = session.current().householdId
        val memberOf = db.householdDao().householdsForUser(user.id)
        val household = memberOf.firstOrNull { it.id == previous } ?: memberOf.firstOrNull()
            ?: households.createHousehold("${user.displayName.substringBefore(' ')}'s Home", HouseholdType.PERSONAL, user.id)
        session.signIn(user.id, household.id)
        user
    }

    suspend fun changePassword(userId: Long, current: String, new: String) = withContext(Dispatchers.IO) {
        val user = db.userDao().findById(userId) ?: throw AuthException("Profile not found.")
        if (!PasswordHasher.verify(current, user.salt, user.passwordHash)) throw AuthException("Current password is incorrect.")
        if (new.length < 6) throw AuthException("New password must be at least 6 characters.")
        val salt = PasswordHasher.newSalt()
        db.userDao().update(user.copy(salt = salt, passwordHash = PasswordHasher.hash(new, salt)))
    }

    suspend fun updateName(userId: Long, name: String) = withContext(Dispatchers.IO) {
        if (name.isBlank()) throw AuthException("Name cannot be empty.")
        val user = db.userDao().findById(userId) ?: return@withContext
        db.userDao().update(user.copy(displayName = name.trim()))
    }

    suspend fun signOut() = session.signOut()
}
