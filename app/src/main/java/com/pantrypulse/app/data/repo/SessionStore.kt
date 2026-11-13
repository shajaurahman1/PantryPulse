package com.pantrypulse.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pantrypulse.app.domain.Expiry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pantrypulse_session")

data class Session(
    val userId: Long?,
    val householdId: Long?,
    val warningDays: Int,
    val notificationsEnabled: Boolean,
    val autoAddFinishedToList: Boolean,
) {
    val isSignedIn: Boolean get() = userId != null && householdId != null
}

class SessionStore(private val context: Context) {
    private object Keys {
        val userId = longPreferencesKey("user_id")
        val householdId = longPreferencesKey("household_id")
        val warningDays = intPreferencesKey("warning_days")
        val notifications = booleanPreferencesKey("notifications_enabled")
        val autoAdd = booleanPreferencesKey("auto_add_finished")
    }

    val session: Flow<Session> = context.dataStore.data.map { p ->
        Session(
            userId = p[Keys.userId],
            householdId = p[Keys.householdId],
            warningDays = p[Keys.warningDays] ?: Expiry.DEFAULT_WARNING_DAYS,
            notificationsEnabled = p[Keys.notifications] ?: true,
            autoAddFinishedToList = p[Keys.autoAdd] ?: false,
        )
    }

    suspend fun current(): Session = session.first()

    suspend fun signIn(userId: Long, householdId: Long) {
        context.dataStore.edit {
            it[Keys.userId] = userId
            it[Keys.householdId] = householdId
        }
    }

    suspend fun switchHousehold(householdId: Long) {
        context.dataStore.edit { it[Keys.householdId] = householdId }
    }

    suspend fun signOut() {
        context.dataStore.edit {
            it.remove(Keys.userId)
            it.remove(Keys.householdId)
        }
    }

    suspend fun setWarningDays(days: Int) {
        context.dataStore.edit { it[Keys.warningDays] = days.coerceIn(1, 14) }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notifications] = enabled }
    }

    suspend fun setAutoAddFinished(enabled: Boolean) {
        context.dataStore.edit { it[Keys.autoAdd] = enabled }
    }
}
