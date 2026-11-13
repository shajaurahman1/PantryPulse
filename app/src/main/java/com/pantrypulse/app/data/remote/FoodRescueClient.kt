package com.pantrypulse.app.data.remote

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RescueSpot(
    val id: String,
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val address: String?,
    val openingHours: String?,
    val phone: String?,
    val website: String?,
    val distanceMeters: Double,
)

data class Place(val name: String, val lat: Double, val lon: Double)

/**
 * Finds places that accept surplus food — food banks, community fridges, food-sharing
 * points, soup kitchens and give-boxes — from OpenStreetMap via the Overpass API.
 */
class FoodRescueClient {
    private val overpassEndpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )

    suspend fun nearby(lat: Double, lon: Double, radiusMeters: Int = 10_000): List<RescueSpot> {
        val around = "around:$radiusMeters,$lat,$lon"
        val query = """
            [out:json][timeout:25];
            (
              nwr["social_facility"="food_bank"]($around);
              nwr["social_facility"="soup_kitchen"]($around);
              nwr["amenity"="food_sharing"]($around);
              nwr["amenity"="give_box"]($around);
              nwr["amenity"="community_fridge"]($around);
              nwr["social_facility:for"="food"]($around);
              nwr["food_bank"="yes"]($around);
            );
            out center tags 60;
        """.trimIndent()
        var lastError: Exception? = null
        for (endpoint in overpassEndpoints) {
            try {
                return parse(Http.postForm(endpoint, mapOf("data" to query)), lat, lon)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Food rescue search failed")
    }

    /** Converts a town or address into coordinates (OpenStreetMap Nominatim). */
    suspend fun geocode(query: String): Place? {
        val arr = JSONArray(Http.get("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${Http.encode(query)}"))
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return Place(o.optString("display_name"), o.getString("lat").toDouble(), o.getString("lon").toDouble())
    }

    companion object {
        fun parse(json: String, fromLat: Double, fromLon: Double): List<RescueSpot> {
            val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
            return (0 until elements.length()).mapNotNull { i ->
                val e = elements.getJSONObject(i)
                val tags = e.optJSONObject("tags") ?: JSONObject()
                val lat = if (e.has("lat")) e.getDouble("lat") else e.optJSONObject("center")?.optDouble("lat") ?: return@mapNotNull null
                val lon = if (e.has("lon")) e.getDouble("lon") else e.optJSONObject("center")?.optDouble("lon") ?: return@mapNotNull null
                val kind = kindOf(tags)
                val address = listOf("addr:housenumber", "addr:street", "addr:city")
                    .mapNotNull { tags.optString(it).ifBlank { null } }
                    .joinToString(" ").ifBlank { null }
                RescueSpot(
                    id = "${e.optString("type")}/${e.optLong("id")}",
                    name = tags.optString("name").ifBlank { tags.optString("operator").ifBlank { kind } },
                    kind = kind,
                    lat = lat, lon = lon,
                    address = address,
                    openingHours = tags.optString("opening_hours").ifBlank { null },
                    phone = tags.optString("phone").ifBlank { tags.optString("contact:phone").ifBlank { null } },
                    website = tags.optString("website").ifBlank { tags.optString("contact:website").ifBlank { null } },
                    distanceMeters = distance(fromLat, fromLon, lat, lon),
                )
            }.distinctBy { it.id }.sortedBy { it.distanceMeters }
        }

        private fun kindOf(tags: JSONObject): String = when {
            tags.optString("social_facility") == "food_bank" || tags.optString("food_bank") == "yes" -> "Food bank"
            tags.optString("social_facility") == "soup_kitchen" -> "Soup kitchen"
            tags.optString("amenity") == "community_fridge" -> "Community fridge"
            tags.optString("amenity") == "give_box" -> "Give box"
            tags.optString("amenity") == "food_sharing" -> "Food sharing point"
            else -> "Food support"
        }

        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return r * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
