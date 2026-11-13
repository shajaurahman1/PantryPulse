package com.pantrypulse.app.data.remote

import com.pantrypulse.app.domain.Recipe
import org.json.JSONObject

data class OnlineRecipeSummary(val id: String, val title: String, val imageUrl: String?, val ingredient: String)

/** Recipe search by ingredient using the free TheMealDB API. */
class MealDbClient {
    private val base = "https://www.themealdb.com/api/json/v1/1"

    suspend fun byIngredient(ingredient: String): List<OnlineRecipeSummary> {
        val json = JSONObject(Http.get("$base/filter.php?i=${Http.encode(ingredient.replace(' ', '_'))}"))
        val meals = json.optJSONArray("meals") ?: return emptyList()
        return (0 until meals.length()).map {
            val m = meals.getJSONObject(it)
            OnlineRecipeSummary("mdb:" + m.getString("idMeal"), m.getString("strMeal"), m.optString("strMealThumb").ifBlank { null }, ingredient)
        }
    }

    suspend fun details(id: String): Recipe? {
        val json = JSONObject(Http.get("$base/lookup.php?i=${Http.encode(id.removePrefix("mdb:"))}"))
        val m = json.optJSONArray("meals")?.optJSONObject(0) ?: return null
        val ingredients = (1..20).mapNotNull { i ->
            val name = m.optString("strIngredient$i").trim()
            val measure = m.optString("strMeasure$i").trim()
            if (name.isEmpty() || name == "null") null else listOf(measure, name).filter { it.isNotEmpty() && it != "null" }.joinToString(" ")
        }
        val steps = m.optString("strInstructions")
            .split(Regex("\\r?\\n+"))
            .map { it.trim().removePrefix("STEP").trim() }
            .filter { it.length > 3 && !it.matches(Regex("^\\d+$")) }
        return Recipe(
            id = "mdb:" + m.getString("idMeal"),
            title = m.getString("strMeal"),
            minutes = 0,
            ingredients = ingredients,
            keywords = ingredients.map { it.lowercase() },
            steps = steps,
            imageUrl = m.optString("strMealThumb").ifBlank { null },
            sourceUrl = m.optString("strSource").takeIf { it.startsWith("http") }
                ?: m.optString("strYoutube").takeIf { it.startsWith("http") },
        )
    }
}
