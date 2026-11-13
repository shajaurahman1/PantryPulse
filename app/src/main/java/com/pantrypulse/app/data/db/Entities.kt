package com.pantrypulse.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pantrypulse.app.domain.Category
import com.pantrypulse.app.domain.HouseholdType
import com.pantrypulse.app.domain.ProductStatus
import java.time.LocalDate

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val email: String,
    val passwordHash: String,
    val salt: String,
    val avatarColor: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "households", indices = [Index(value = ["inviteCode"], unique = true)])
data class HouseholdEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: HouseholdType,
    val inviteCode: String,
    val ownerId: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "memberships",
    primaryKeys = ["userId", "householdId"],
    foreignKeys = [
        ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(HouseholdEntity::class, ["id"], ["householdId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("householdId")],
)
data class MembershipEntity(
    val userId: Long,
    val householdId: Long,
    val role: String,
    val joinedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "locations",
    foreignKeys = [ForeignKey(HouseholdEntity::class, ["id"], ["householdId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("householdId")],
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val householdId: Long,
    val name: String,
    val iconKey: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(HouseholdEntity::class, ["id"], ["householdId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("householdId"), Index("locationId"), Index("barcode")],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val householdId: Long,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val category: Category = Category.OTHER,
    val locationId: Long? = null,
    val quantity: Double = 1.0,
    val unit: String = "pcs",
    val expiryDate: LocalDate? = null,
    val purchaseDate: LocalDate? = null,
    val imageUrl: String? = null,
    val notes: String = "",
    val status: ProductStatus = ProductStatus.ACTIVE,
    val addedBy: Long,
    val finishedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "shopping_items",
    foreignKeys = [ForeignKey(HouseholdEntity::class, ["id"], ["householdId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("householdId")],
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val householdId: Long,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "pcs",
    val category: Category = Category.OTHER,
    val checked: Boolean = false,
    val addedBy: Long,
    val addedByName: String,
    val sourceProductId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Row used by the members list: a user plus their role in the household. */
data class MemberRow(
    val id: Long,
    val displayName: String,
    val email: String,
    val avatarColor: Int,
    val role: String,
)
