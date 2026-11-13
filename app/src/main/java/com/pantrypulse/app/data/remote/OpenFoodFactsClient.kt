package com.pantrypulse.app.data.remote

import com.pantrypulse.app.domain.Category
import com.pantrypulse.app.domain.CategoryGuesser
import org.json.JSONObject

data class BarcodeProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val category: Category,
    val imageUrl: String?,
    val packageSize: String?,
)

/** Looks up packaged foods by barcode in the free Open Food Facts database. */
class OpenFoodFactsClient {
    suspend fun lookup(barcode: String): BarcodeProduct? {
        val json = Http.get(
            "https://world.openfoodfacts.org/api/v2/product/${Http.encode(barcode)}.json" +
                "?fields=product_name,product_name_en,generic_name,brands,categories_tags,image_front_small_url,quantity"
        )
        return parse(barcode, json)
    }

    companion object {
        fun parse(barcode: String, json: String): BarcodeProduct? {
            val root = JSONObject(json)
            if (root.optInt("status", 0) != 1) return null
            val p = root.optJSONObject("product") ?: return null
            val name = listOf("product_name", "product_name_en", "generic_name")
                .map { p.optString(it).trim() }.firstOrNull { it.isNotEmpty() } ?: return null
            val brand = p.optString("brands").split(',').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            val tagsJson = p.optJSONArray("categories_tags")
            val tags = (0 until (tagsJson?.length() ?: 0)).map { tagsJson!!.getString(it) }
            return BarcodeProduct(
                barcode = barcode,
                name = name,
                brand = brand,
                category = CategoryGuesser.fromOpenFoodFactsTags(tags, name),
                imageUrl = p.optString("image_front_small_url").takeIf { it.startsWith("http") },
                packageSize = p.optString("quantity").takeIf { it.isNotBlank() },
            )
        }
    }
}
