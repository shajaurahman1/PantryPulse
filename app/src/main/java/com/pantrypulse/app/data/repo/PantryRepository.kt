package com.pantrypulse.app.data.repo

import com.pantrypulse.app.data.db.AppDatabase
import com.pantrypulse.app.data.db.LocationEntity
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.domain.ProductStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

class PantryRepository(private val db: AppDatabase) {
    private val products get() = db.productDao()
    private val locations get() = db.locationDao()

    /** Serialises read-modify-write updates so quick repeated taps never lose a change. */
    private val writeLock = Mutex()

    fun observeActive(householdId: Long) = products.observeActive(householdId)
    fun observeHistory(householdId: Long) = products.observeHistory(householdId)
    fun observeStatusCounts(householdId: Long) = products.observeStatusCounts(householdId)
    fun observeProduct(id: Long) = products.observe(id)
    fun observeLocations(householdId: Long) = locations.observe(householdId)

    suspend fun locations(householdId: Long) = withContext(Dispatchers.IO) { locations.list(householdId) }
    suspend fun product(id: Long) = withContext(Dispatchers.IO) { products.findById(id) }
    suspend fun latestByBarcode(householdId: Long, barcode: String) =
        withContext(Dispatchers.IO) { products.findLatestByBarcode(householdId, barcode) }

    suspend fun save(product: ProductEntity): Long = withContext(Dispatchers.IO) {
        require(product.name.isNotBlank()) { "Product name is required." }
        if (product.id == 0L) products.insert(product)
        else { products.update(product.copy(updatedAt = System.currentTimeMillis())); product.id }
    }

    suspend fun saveAll(items: List<ProductEntity>) = withContext(Dispatchers.IO) { products.insertAll(items) }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { products.delete(id) }

    /**
     * Uses [amount] of a product. Returns the updated product; when the quantity reaches
     * zero it is marked finished so the UI can offer to add it to the shopping list.
     */
    suspend fun consume(id: Long, amount: Double = 1.0): ProductEntity? = withContext(Dispatchers.IO) { writeLock.withLock {
        val p = products.findById(id) ?: return@withContext null
        val left = (p.quantity - amount).coerceAtLeast(0.0)
        val updated = if (left <= 0.0001) {
            p.copy(quantity = 0.0, status = ProductStatus.FINISHED, finishedAt = System.currentTimeMillis())
        } else p.copy(quantity = left)
        products.update(updated.copy(updatedAt = System.currentTimeMillis()))
        updated
    } }

    suspend fun setStatus(id: Long, status: ProductStatus): ProductEntity? = withContext(Dispatchers.IO) { writeLock.withLock {
        val p = products.findById(id) ?: return@withContext null
        val updated = p.copy(
            status = status,
            finishedAt = if (status == ProductStatus.ACTIVE) null else System.currentTimeMillis(),
            quantity = if (status == ProductStatus.ACTIVE && p.quantity <= 0) 1.0 else p.quantity,
            updatedAt = System.currentTimeMillis(),
        )
        products.update(updated)
        updated
    } }

    suspend fun expiringWithin(householdId: Long, days: Int, today: LocalDate = LocalDate.now()) =
        withContext(Dispatchers.IO) { products.expiringBefore(householdId, today.plusDays(days.toLong()).toEpochDay()) }

    suspend fun addLocation(householdId: Long, name: String, iconKey: String = "pantry") = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Location name is required." }
        val order = locations.list(householdId).maxOfOrNull { it.sortOrder + 1 } ?: 0
        locations.insert(LocationEntity(householdId = householdId, name = name.trim(), iconKey = iconKey, sortOrder = order))
    }

    suspend fun renameLocation(location: LocationEntity, name: String) = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Location name is required." }
        locations.update(location.copy(name = name.trim()))
    }

    suspend fun deleteLocation(location: LocationEntity) = withContext(Dispatchers.IO) {
        locations.clearFromProducts(location.id)
        locations.delete(location)
    }
}
