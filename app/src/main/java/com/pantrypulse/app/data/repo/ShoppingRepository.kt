package com.pantrypulse.app.data.repo

import com.pantrypulse.app.data.db.AppDatabase
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.data.db.ShoppingItemEntity
import com.pantrypulse.app.domain.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class ShoppingRepository(private val db: AppDatabase) {
    private val dao get() = db.shoppingDao()

    fun observe(householdId: Long) = dao.observe(householdId)

    suspend fun add(
        householdId: Long, name: String, userId: Long, userName: String,
        quantity: Double = 1.0, unit: String = "pcs", category: Category = Category.OTHER,
        sourceProductId: Long? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Item name is required." }
        // Do not add a duplicate of something already waiting on the list.
        if (dao.findOpenByName(householdId, name.trim()) != null) return@withContext false
        dao.insert(
            ShoppingItemEntity(
                householdId = householdId, name = name.trim(), quantity = quantity, unit = unit,
                category = category, addedBy = userId, addedByName = userName, sourceProductId = sourceProductId,
            )
        )
        true
    }

    /** The one-tap "finished → shopping list" action. */
    suspend fun addFromProduct(product: ProductEntity, userId: Long, userName: String): Boolean =
        add(product.householdId, product.name, userId, userName, 1.0, product.unit.takeIf { it != "g" && it != "ml" } ?: "pcs", product.category, product.id)

    suspend fun toggle(item: ShoppingItemEntity) = withContext(Dispatchers.IO) { dao.update(item.copy(checked = !item.checked)) }
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }
    suspend fun clearChecked(householdId: Long) = withContext(Dispatchers.IO) { dao.clearChecked(householdId) }

    /** Moves bought (checked) items into the pantry with a suggested expiry date. */
    suspend fun moveCheckedToPantry(householdId: Long, userId: Long): Int = withContext(Dispatchers.IO) {
        val checked = dao.checkedItems(householdId)
        if (checked.isEmpty()) return@withContext 0
        val locations = db.locationDao().list(householdId)
        val today = LocalDate.now()
        db.productDao().insertAll(checked.map { item ->
            ProductEntity(
                householdId = householdId,
                name = item.name,
                category = item.category,
                quantity = item.quantity,
                unit = item.unit,
                locationId = locations.firstOrNull { it.name.equals(item.category.defaultLocation.label, true) }?.id,
                expiryDate = item.category.suggestedExpiry(today),
                purchaseDate = today,
                addedBy = userId,
            )
        })
        dao.clearChecked(householdId)
        checked.size
    }
}
