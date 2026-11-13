package com.pantrypulse.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ProductStatus { ACTIVE, FINISHED, DONATED, WASTED }

enum class HouseholdType(val label: String, val description: String) {
    PERSONAL("Personal", "Just me"),
    FAMILY("Family", "Everyone at home shares one pantry"),
    ROOMMATES("Roommates", "Flatmates sharing a kitchen"),
    OFFICE("Office breakroom", "Colleagues sharing a breakroom"),
}

/** Default storage spots created for every new household. Users can add their own. */
enum class DefaultLocation(val label: String, val iconKey: String) {
    FRIDGE("Fridge", "fridge"),
    FREEZER("Freezer", "freezer"),
    PANTRY("Pantry", "pantry"),
    COUNTER("Counter", "counter"),
    CUPBOARD("Cupboard", "cupboard"),
    BATHROOM("Bathroom", "bathroom"),
    MEDICINE_CABINET("Medicine cabinet", "medicine"),
}

/** Top-level kind of item, matching the Food / Cosmetics / Medicine tabs. */
enum class ItemKind(val label: String) { FOOD("Food"), COSMETICS("Cosmetics"), MEDICINE("Medicine") }

enum class Category(
    val label: String,
    val emoji: String,
    /** Typical shelf life after purchase, used to suggest an expiry date when none is printed. */
    val shelfLifeDays: Int,
    val defaultLocation: DefaultLocation,
    val kind: ItemKind = ItemKind.FOOD,
) {
    FRUIT("Fruit", "🍎", 7, DefaultLocation.COUNTER),
    VEGETABLES("Vegetables", "🥦", 7, DefaultLocation.FRIDGE),
    DAIRY("Dairy", "🥛", 7, DefaultLocation.FRIDGE),
    EGGS("Eggs", "🥚", 21, DefaultLocation.FRIDGE),
    MEAT("Meat", "🥩", 3, DefaultLocation.FRIDGE),
    SEAFOOD("Seafood", "🐟", 2, DefaultLocation.FRIDGE),
    BAKERY("Bakery", "🍞", 4, DefaultLocation.PANTRY),
    FROZEN("Frozen", "🧊", 90, DefaultLocation.FREEZER),
    GRAINS("Grains & pasta", "🍚", 365, DefaultLocation.PANTRY),
    CANNED("Canned & jarred", "🥫", 365, DefaultLocation.CUPBOARD),
    SNACKS("Snacks", "🍪", 60, DefaultLocation.CUPBOARD),
    BEVERAGES("Beverages", "🧃", 60, DefaultLocation.PANTRY),
    CONDIMENTS("Sauces & spices", "🧂", 180, DefaultLocation.CUPBOARD),
    READY_MEALS("Ready meals", "🍱", 3, DefaultLocation.FRIDGE),
    OTHER("Other food", "📦", 14, DefaultLocation.PANTRY),
    SKINCARE("Skincare & makeup", "💄", 365, DefaultLocation.BATHROOM, ItemKind.COSMETICS),
    TOILETRIES("Toiletries", "🧴", 540, DefaultLocation.BATHROOM, ItemKind.COSMETICS),
    MEDICINE("Medicine", "💊", 365, DefaultLocation.MEDICINE_CABINET, ItemKind.MEDICINE),
    FIRST_AID("First aid & vitamins", "🩹", 365, DefaultLocation.MEDICINE_CABINET, ItemKind.MEDICINE);

    fun suggestedExpiry(from: LocalDate = LocalDate.now()): LocalDate = from.plusDays(shelfLifeDays.toLong())
}

enum class ExpiryStatus { EXPIRED, TODAY, SOON, FRESH, NO_DATE }

object Expiry {
    const val DEFAULT_WARNING_DAYS = 3

    fun daysLeft(expiry: LocalDate?, today: LocalDate = LocalDate.now()): Long? =
        expiry?.let { ChronoUnit.DAYS.between(today, it) }

    fun status(expiry: LocalDate?, warningDays: Int = DEFAULT_WARNING_DAYS, today: LocalDate = LocalDate.now()): ExpiryStatus {
        val days = daysLeft(expiry, today) ?: return ExpiryStatus.NO_DATE
        return when {
            days < 0 -> ExpiryStatus.EXPIRED
            days == 0L -> ExpiryStatus.TODAY
            days <= warningDays -> ExpiryStatus.SOON
            else -> ExpiryStatus.FRESH
        }
    }

    fun label(expiry: LocalDate?, today: LocalDate = LocalDate.now()): String {
        val days = daysLeft(expiry, today) ?: return "No expiry date"
        return when {
            days < -1 -> "Expired ${-days} days ago"
            days == -1L -> "Expired yesterday"
            days == 0L -> "Expires today"
            days == 1L -> "Expires tomorrow"
            days < 14 -> "$days days left"
            days < 60 -> "${days / 7} weeks left"
            else -> "${days / 30} months left"
        }
    }
}

/** Guesses a product category from its name, for barcode lookups and receipt lines. */
object CategoryGuesser {
    private val keywords: List<Pair<Category, List<String>>> = listOf(
        Category.MEDICINE to listOf("tablet", "capsule", "syrup", "paracetamol", "ibuprofen", "aspirin", "antibiotic", "medicine", "drops", "ointment", "mg "),
        Category.FIRST_AID to listOf("vitamin", "plaster", "bandage", "antiseptic", "supplement"),
        Category.SKINCARE to listOf("lipstick", "mascara", "foundation", "serum", "moisturi", "sunscreen", "spf", "makeup", "face cream", "lotion"),
        Category.TOILETRIES to listOf("shampoo", "conditioner", "toothpaste", "soap", "deodorant", "body wash", "shower gel"),
        Category.EGGS to listOf("egg"),
        Category.DAIRY to listOf("milk", "cheese", "yogurt", "yoghurt", "butter", "cream", "curd", "paneer", "ghee", "kefir", "mozzarella", "cheddar", "dahi"),
        Category.SEAFOOD to listOf("fish", "salmon", "tuna fillet", "prawn", "shrimp", "cod", "crab", "hilsa", "seafood", "mackerel"),
        Category.MEAT to listOf("chicken", "beef", "pork", "lamb", "mutton", "mince", "sausage", "bacon", "ham", "turkey", "steak", "meat", "salami"),
        Category.FROZEN to listOf("frozen", "ice cream", "nuggets", "fries"),
        Category.BAKERY to listOf("bread", "bun", "bagel", "croissant", "cake", "muffin", "roll", "baguette", "tortilla", "naan", "pita"),
        Category.FRUIT to listOf("apple", "banana", "orange", "grape", "mango", "berry", "berries", "strawberr", "lemon", "lime", "pear", "kiwi", "melon", "pineapple", "peach", "plum", "avocado", "papaya", "guava"),
        Category.VEGETABLES to listOf("tomato", "potato", "onion", "carrot", "lettuce", "spinach", "cucumber", "pepper", "broccoli", "cabbage", "garlic", "ginger", "mushroom", "salad", "zucchini", "cauliflower", "beans", "peas", "brinjal", "eggplant", "chilli", "coriander", "celery", "kale"),
        Category.GRAINS to listOf("rice", "pasta", "spaghetti", "noodle", "flour", "oats", "cereal", "atta", "quinoa", "lentil", "dal", "macaroni", "couscous", "semolina"),
        Category.CANNED to listOf("canned", "tin", "beans in", "soup", "tuna", "jam", "pickle", "corn"),
        Category.BEVERAGES to listOf("juice", "water", "soda", "cola", "coffee", "tea", "drink", "beer", "wine", "lemonade"),
        Category.SNACKS to listOf("chips", "crisps", "biscuit", "cookie", "chocolate", "candy", "nuts", "crackers", "popcorn", "bar"),
        Category.CONDIMENTS to listOf("sauce", "ketchup", "mayo", "mustard", "oil", "vinegar", "salt", "sugar", "spice", "masala", "honey", "dressing", "soy"),
        Category.READY_MEALS to listOf("ready meal", "sandwich", "pizza", "wrap", "sushi", "meal"),
    )

    fun guess(text: String): Category {
        val t = text.lowercase()
        return keywords.firstOrNull { (_, words) -> words.any { t.contains(it) } }?.first ?: Category.OTHER
    }

    /** Maps Open Food Facts category tags such as "en:dairies" onto our categories. */
    fun fromOpenFoodFactsTags(tags: List<String>, name: String): Category {
        val joined = tags.joinToString(" ").lowercase()
        return when {
            "medicine" in joined || "drug" in joined -> Category.MEDICINE
            "cosmetic" in joined || "beauty" in joined -> Category.SKINCARE
            "egg" in joined -> Category.EGGS
            "dair" in joined || "milk" in joined || "cheese" in joined || "yogurt" in joined -> Category.DAIRY
            "seafood" in joined || "fish" in joined -> Category.SEAFOOD
            "meat" in joined || "poultr" in joined -> Category.MEAT
            "frozen" in joined -> Category.FROZEN
            "bread" in joined || "bakery" in joined || "pastr" in joined -> Category.BAKERY
            "fruit" in joined && "juice" !in joined -> Category.FRUIT
            "vegetable" in joined -> Category.VEGETABLES
            "beverage" in joined || "drink" in joined || "juice" in joined || "water" in joined -> Category.BEVERAGES
            "snack" in joined || "biscuit" in joined || "chocolate" in joined || "confectioner" in joined -> Category.SNACKS
            "sauce" in joined || "condiment" in joined || "spice" in joined || "oil" in joined -> Category.CONDIMENTS
            "cereal" in joined || "pasta" in joined || "rice" in joined || "grain" in joined -> Category.GRAINS
            "canned" in joined -> Category.CANNED
            "meal" in joined -> Category.READY_MEALS
            else -> guess(name)
        }
    }
}
