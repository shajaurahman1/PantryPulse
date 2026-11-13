package com.pantrypulse.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Update suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE lower(email) = lower(:email) LIMIT 1")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observe(id: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<UserEntity>>
}

@Dao
interface HouseholdDao {
    @Insert suspend fun insert(household: HouseholdEntity): Long
    @Update suspend fun update(household: HouseholdEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembership(membership: MembershipEntity)

    @Query("DELETE FROM memberships WHERE userId = :userId AND householdId = :householdId")
    suspend fun deleteMembership(userId: Long, householdId: Long)

    @Query("SELECT * FROM households WHERE id = :id")
    suspend fun findById(id: Long): HouseholdEntity?

    @Query("SELECT * FROM households WHERE id = :id")
    fun observe(id: Long): Flow<HouseholdEntity?>

    @Query("SELECT * FROM households WHERE upper(inviteCode) = upper(:code) LIMIT 1")
    suspend fun findByInviteCode(code: String): HouseholdEntity?

    @Query(
        """SELECT h.* FROM households h JOIN memberships m ON m.householdId = h.id
           WHERE m.userId = :userId ORDER BY h.createdAt"""
    )
    fun observeForUser(userId: Long): Flow<List<HouseholdEntity>>

    @Query(
        """SELECT h.* FROM households h JOIN memberships m ON m.householdId = h.id
           WHERE m.userId = :userId ORDER BY h.createdAt"""
    )
    suspend fun householdsForUser(userId: Long): List<HouseholdEntity>

    @Query(
        """SELECT u.id, u.displayName, u.email, u.avatarColor, m.role FROM users u
           JOIN memberships m ON m.userId = u.id WHERE m.householdId = :householdId
           ORDER BY m.joinedAt"""
    )
    fun observeMembers(householdId: Long): Flow<List<MemberRow>>

    @Query("SELECT COUNT(*) FROM memberships WHERE userId = :userId AND householdId = :householdId")
    suspend fun isMember(userId: Long, householdId: Long): Int
}

@Dao
interface LocationDao {
    @Insert suspend fun insertAll(locations: List<LocationEntity>)
    @Insert suspend fun insert(location: LocationEntity): Long
    @Update suspend fun update(location: LocationEntity)
    @Delete suspend fun delete(location: LocationEntity)

    @Query("SELECT * FROM locations WHERE householdId = :householdId ORDER BY sortOrder, name")
    fun observe(householdId: Long): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE householdId = :householdId ORDER BY sortOrder, name")
    suspend fun list(householdId: Long): List<LocationEntity>

    @Query("UPDATE products SET locationId = NULL WHERE locationId = :locationId")
    suspend fun clearFromProducts(locationId: Long)
}

@Dao
interface ProductDao {
    @Insert suspend fun insert(product: ProductEntity): Long
    @Insert suspend fun insertAll(products: List<ProductEntity>)
    @Update suspend fun update(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun findById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    fun observe(id: Long): Flow<ProductEntity?>

    @Query(
        """SELECT * FROM products WHERE householdId = :householdId AND status = 'ACTIVE'
           ORDER BY CASE WHEN expiryDate IS NULL THEN 1 ELSE 0 END, expiryDate, name COLLATE NOCASE"""
    )
    fun observeActive(householdId: Long): Flow<List<ProductEntity>>

    @Query(
        """SELECT * FROM products WHERE householdId = :householdId AND status = 'ACTIVE'
           AND expiryDate IS NOT NULL AND expiryDate <= :lastDay ORDER BY expiryDate"""
    )
    suspend fun expiringBefore(householdId: Long, lastDay: Long): List<ProductEntity>

    @Query(
        """SELECT * FROM products WHERE householdId = :householdId AND status != 'ACTIVE'
           ORDER BY finishedAt DESC LIMIT :limit"""
    )
    fun observeHistory(householdId: Long, limit: Int = 30): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE householdId = :householdId AND barcode = :barcode ORDER BY createdAt DESC LIMIT 1")
    suspend fun findLatestByBarcode(householdId: Long, barcode: String): ProductEntity?

    @Query("SELECT status, COUNT(*) AS count FROM products WHERE householdId = :householdId GROUP BY status")
    fun observeStatusCounts(householdId: Long): Flow<List<StatusCount>>
}

data class StatusCount(val status: String, val count: Int)

@Dao
interface ShoppingDao {
    @Insert suspend fun insert(item: ShoppingItemEntity): Long
    @Update suspend fun update(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM shopping_items WHERE householdId = :householdId ORDER BY checked, createdAt DESC")
    fun observe(householdId: Long): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE householdId = :householdId AND checked = 1")
    suspend fun checkedItems(householdId: Long): List<ShoppingItemEntity>

    @Query("DELETE FROM shopping_items WHERE householdId = :householdId AND checked = 1")
    suspend fun clearChecked(householdId: Long)

    @Query(
        """SELECT * FROM shopping_items WHERE householdId = :householdId AND checked = 0
           AND lower(name) = lower(:name) LIMIT 1"""
    )
    suspend fun findOpenByName(householdId: Long, name: String): ShoppingItemEntity?
}
