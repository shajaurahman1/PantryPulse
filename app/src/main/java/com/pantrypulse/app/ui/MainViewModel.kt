package com.pantrypulse.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrypulse.app.AppContainer
import com.pantrypulse.app.data.db.HouseholdEntity
import com.pantrypulse.app.data.db.LocationEntity
import com.pantrypulse.app.data.db.MemberRow
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.data.db.ShoppingItemEntity
import com.pantrypulse.app.data.db.UserEntity
import com.pantrypulse.app.data.repo.Session
import com.pantrypulse.app.domain.ProductStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A message for the snackbar, optionally with a one-tap action. */
data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(val container: AppContainer) : ViewModel() {

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** Null until the stored session has loaded, so the UI can show the splash instead of flashing sign-in. */
    val session: StateFlow<Session?> = container.session.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val userId = session.map { it?.userId }.distinctUntilChanged()
    private val householdId = session.map { it?.householdId }.distinctUntilChanged()

    private fun <T> byUser(default: T, block: (Long) -> Flow<T>): StateFlow<T> =
        userId.flatMapLatest { id -> if (id == null) flowOf(default) else block(id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, default)

    private fun <T> byHousehold(default: T, block: (Long) -> Flow<T>): StateFlow<T> =
        householdId.flatMapLatest { id -> if (id == null) flowOf(default) else block(id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, default)

    val user: StateFlow<UserEntity?> = byUser(null) { container.db.userDao().observe(it) }
    val households: StateFlow<List<HouseholdEntity>> = byUser(emptyList()) { container.households.observeForUser(it) }
    val household: StateFlow<HouseholdEntity?> = byHousehold(null) { container.households.observe(it) }
    val members: StateFlow<List<MemberRow>> = byHousehold(emptyList()) { container.households.observeMembers(it) }
    val products: StateFlow<List<ProductEntity>> = byHousehold(emptyList()) { container.pantry.observeActive(it) }
    val history: StateFlow<List<ProductEntity>> = byHousehold(emptyList()) { container.pantry.observeHistory(it) }
    val locations: StateFlow<List<LocationEntity>> = byHousehold(emptyList()) { container.pantry.observeLocations(it) }
    val shopping: StateFlow<List<ShoppingItemEntity>> = byHousehold(emptyList()) { container.shopping.observe(it) }
    val statusCounts: StateFlow<Map<String, Int>> = byHousehold(emptyMap()) { id ->
        container.pantry.observeStatusCounts(id).map { rows -> rows.associate { it.status to it.count } }
    }

    val allUsers: StateFlow<List<UserEntity>> = container.db.userDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun message(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _messages.trySend(UiMessage(text, actionLabel, action))
    }

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            message(e.message ?: "Something went wrong")
        }
    }

    // ---- Pantry actions -------------------------------------------------------------

    fun useOne(product: ProductEntity) = launchSafe {
        val updated = container.pantry.consume(product.id, 1.0) ?: return@launchSafe
        if (updated.status == ProductStatus.FINISHED) onFinished(updated)
        else message("Used one ${product.name}. ${com.pantrypulse.app.ui.components.formatQty(updated.quantity, updated.unit)} left.", "Undo") {
            launchSafe { container.pantry.save(updated.copy(quantity = product.quantity)) }
        }
    }

    fun markFinished(product: ProductEntity) = launchSafe {
        val updated = container.pantry.setStatus(product.id, ProductStatus.FINISHED) ?: return@launchSafe
        onFinished(updated)
    }

    /** When something runs out, offer (or perform) the one-tap move to the shopping list. */
    private suspend fun onFinished(product: ProductEntity) {
        val s = session.value ?: return
        if (s.autoAddFinishedToList) {
            addToShopping(product, quiet = true)
            message("${product.name} is finished and was added to the shopping list.")
        } else {
            message("${product.name} is finished.", "Add to list") { addToShopping(product) }
        }
    }

    fun addToShopping(product: ProductEntity, quiet: Boolean = false) = launchSafe {
        val u = user.value ?: return@launchSafe
        val added = container.shopping.addFromProduct(product, u.id, u.displayName)
        if (!quiet) message(if (added) "${product.name} added to the shopping list." else "${product.name} is already on the shopping list.")
    }

    fun markDonated(product: ProductEntity) = launchSafe {
        container.pantry.setStatus(product.id, ProductStatus.DONATED)
        message("Thank you for rescuing ${product.name}!", "Undo") { restore(product) }
    }

    fun markWasted(product: ProductEntity) = launchSafe {
        container.pantry.setStatus(product.id, ProductStatus.WASTED)
        message("${product.name} logged as thrown away.", "Undo") { restore(product) }
    }

    fun restore(product: ProductEntity) = launchSafe {
        container.pantry.setStatus(product.id, ProductStatus.ACTIVE)
    }

    fun delete(product: ProductEntity) = launchSafe {
        container.pantry.delete(product.id)
        message("${product.name} deleted.", "Undo") { launchSafe { container.pantry.save(product.copy(id = 0)) } }
    }

    // ---- Shopping list --------------------------------------------------------------

    fun addShoppingItem(name: String) = launchSafe {
        val u = user.value ?: return@launchSafe
        val hh = session.value?.householdId ?: return@launchSafe
        val added = container.shopping.add(hh, name, u.id, u.displayName, category = com.pantrypulse.app.domain.CategoryGuesser.guess(name))
        if (!added) message("$name is already on the list.")
    }

    fun toggleShopping(item: ShoppingItemEntity) = launchSafe { container.shopping.toggle(item) }
    fun deleteShopping(item: ShoppingItemEntity) = launchSafe { container.shopping.delete(item.id) }
    fun clearChecked() = launchSafe { container.shopping.clearChecked(session.value?.householdId ?: return@launchSafe) }

    fun moveBoughtToPantry() = launchSafe {
        val hh = session.value?.householdId ?: return@launchSafe
        val u = user.value ?: return@launchSafe
        val n = container.shopping.moveCheckedToPantry(hh, u.id)
        message(if (n == 0) "Tick the items you bought first." else "$n item${if (n > 1) "s" else ""} moved to your pantry with suggested expiry dates.")
    }

    // ---- Settings -------------------------------------------------------------------

    fun setWarningDays(days: Int) = launchSafe { container.session.setWarningDays(days) }
    fun setNotifications(enabled: Boolean) = launchSafe { container.session.setNotificationsEnabled(enabled) }
    fun setAutoAdd(enabled: Boolean) = launchSafe { container.session.setAutoAddFinished(enabled) }

    fun switchHousehold(id: Long) = launchSafe { container.households.switchTo(id) }

    fun signOut() = launchSafe { container.auth.signOut() }
}
