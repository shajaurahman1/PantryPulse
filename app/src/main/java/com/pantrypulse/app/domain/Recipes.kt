package com.pantrypulse.app.domain

data class Recipe(
    val id: String,
    val title: String,
    val minutes: Int,
    val ingredients: List<String>,
    /** Lower-case words used to match pantry items to this recipe. */
    val keywords: List<String>,
    val steps: List<String>,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
)

data class RecipeSuggestion(
    val recipe: Recipe,
    val matched: List<String>,
    val usesExpiring: List<String>,
    val score: Int,
)

/** Ranks recipes by how many pantry items they use, weighting items that expire soon. */
object RecipeMatcher {

    fun suggest(
        recipes: List<Recipe>,
        pantryNames: List<String>,
        expiringNames: List<String>,
    ): List<RecipeSuggestion> {
        val pantry = pantryNames.map { it.lowercase() }
        val expiring = expiringNames.map { it.lowercase() }
        return recipes.mapNotNull { recipe ->
            val matched = recipe.keywords.filter { k -> pantry.any { it.contains(k) } }
            if (matched.isEmpty()) return@mapNotNull null
            val usesExpiring = recipe.keywords.filter { k -> expiring.any { it.contains(k) } }
            val coverage = matched.size * 100 / recipe.keywords.size
            RecipeSuggestion(recipe, matched, usesExpiring, coverage + usesExpiring.size * 60)
        }.sortedByDescending { it.score }
    }

    /** Picks the main ingredient word of a product name for online recipe search. */
    fun ingredientKeyword(productName: String): String? {
        val n = productName.lowercase()
        return searchable.firstOrNull { n.contains(it) }
    }

    private val searchable = listOf(
        "chicken", "beef", "pork", "lamb", "salmon", "tuna", "prawns", "shrimp", "egg", "milk", "cheese",
        "yogurt", "butter", "cream", "rice", "pasta", "spaghetti", "bread", "potato", "tomato", "onion",
        "carrot", "spinach", "mushroom", "broccoli", "cabbage", "garlic", "ginger", "lemon", "lime",
        "banana", "apple", "mango", "strawberries", "avocado", "cucumber", "pepper", "lentils", "beans",
        "chickpeas", "flour", "oats", "bacon", "sausage", "ham", "corn", "peas", "zucchini", "aubergine",
    )

    val builtIn: List<Recipe> = listOf(
        Recipe("local:fried-rice", "Clear-the-fridge fried rice", 20,
            listOf("2 cups cooked rice", "2 eggs", "1 cup mixed vegetables", "2 spring onions", "2 tbsp soy sauce", "1 tbsp oil"),
            listOf("rice", "egg", "carrot", "peas", "onion", "pepper"),
            listOf("Heat oil in a wok and scramble the eggs, then set aside.", "Stir-fry the chopped vegetables for 3–4 minutes.", "Add the rice and break up any clumps; fry until hot.", "Return the eggs, add soy sauce and spring onions, toss and serve.")),
        Recipe("local:frittata", "Leftover veggie frittata", 25,
            listOf("6 eggs", "1/2 cup milk", "1 cup chopped vegetables", "1/2 cup grated cheese", "Salt and pepper"),
            listOf("egg", "milk", "cheese", "spinach", "tomato", "mushroom", "pepper", "onion"),
            listOf("Heat the oven grill.", "Soften vegetables in an oven-safe pan.", "Whisk eggs, milk and seasoning; pour over the vegetables.", "Cook on low for 6 minutes, top with cheese and grill until set and golden.")),
        Recipe("local:banana-bread", "Ripe banana bread", 60,
            listOf("3 overripe bananas", "1/3 cup melted butter", "1/2 cup sugar", "1 egg", "1 tsp baking soda", "1 1/2 cups flour"),
            listOf("banana", "butter", "egg", "flour", "sugar"),
            listOf("Heat oven to 175°C and grease a loaf tin.", "Mash bananas and mix in melted butter.", "Stir in sugar, egg and baking soda, then fold in the flour.", "Bake 50–60 minutes until a skewer comes out clean.")),
        Recipe("local:smoothie", "Use-it-up fruit smoothie", 5,
            listOf("1 banana", "1 cup berries or mango", "1 cup milk or yogurt", "1 tsp honey"),
            listOf("banana", "berr", "strawberr", "mango", "milk", "yogurt", "apple"),
            listOf("Add everything to a blender.", "Blend until smooth and serve straight away.")),
        Recipe("local:veg-soup", "Any-vegetable soup", 35,
            listOf("4 cups chopped vegetables", "1 onion", "2 garlic cloves", "1 L stock", "1 tbsp oil", "Salt and pepper"),
            listOf("carrot", "potato", "onion", "tomato", "celery", "cabbage", "broccoli", "zucchini", "spinach", "garlic"),
            listOf("Soften onion and garlic in oil.", "Add the vegetables and stock; simmer 20 minutes.", "Blend part or all of the soup, season and serve.")),
        Recipe("local:pasta-bake", "Cheesy pasta bake", 40,
            listOf("300 g pasta", "1 jar tomato sauce", "1 cup vegetables or cooked meat", "1 cup grated cheese"),
            listOf("pasta", "spaghetti", "macaroni", "tomato", "cheese", "chicken", "sausage", "mushroom", "spinach"),
            listOf("Boil pasta until just under done.", "Mix with sauce and vegetables or meat in a baking dish.", "Top with cheese and bake at 200°C for 20 minutes.")),
        Recipe("local:chicken-curry", "Quick chicken curry", 40,
            listOf("500 g chicken", "1 onion", "2 tomatoes", "1 tbsp ginger-garlic paste", "2 tbsp curry powder", "1/2 cup yogurt or cream"),
            listOf("chicken", "onion", "tomato", "garlic", "ginger", "yogurt", "cream"),
            listOf("Fry the onion until golden, add ginger-garlic paste and curry powder.", "Add chopped tomatoes and cook to a thick paste.", "Add chicken and cook through, 15–20 minutes.", "Stir in yogurt or cream off the heat and serve with rice or bread.")),
        Recipe("local:french-toast", "Day-old bread French toast", 15,
            listOf("4 slices stale bread", "2 eggs", "1/2 cup milk", "1 tsp cinnamon", "Butter for frying"),
            listOf("bread", "egg", "milk", "butter"),
            listOf("Whisk eggs, milk and cinnamon.", "Soak bread slices briefly.", "Fry in butter until golden on both sides.")),
        Recipe("local:omelette", "Fridge-raid omelette", 10,
            listOf("3 eggs", "Handful of cheese", "Any chopped vegetables or ham"),
            listOf("egg", "cheese", "ham", "tomato", "mushroom", "spinach", "onion", "pepper"),
            listOf("Whisk eggs with a pinch of salt.", "Pour into a hot buttered pan and swirl.", "Add fillings, fold and serve.")),
        Recipe("local:stir-fry", "Ten-minute stir-fry", 15,
            listOf("Noodles or rice", "Mixed vegetables", "Protein of choice", "Soy sauce", "Garlic and ginger"),
            listOf("noodle", "rice", "chicken", "beef", "prawn", "broccoli", "pepper", "carrot", "cabbage", "mushroom"),
            listOf("Stir-fry protein in a very hot pan, then remove.", "Fry garlic, ginger and vegetables 3 minutes.", "Return protein, add sauce and noodles or rice, toss and serve.")),
        Recipe("local:salad", "Big crunchy salad", 10,
            listOf("Lettuce or spinach", "Cucumber", "Tomato", "Cheese or boiled egg", "Olive oil and lemon"),
            listOf("lettuce", "spinach", "cucumber", "tomato", "pepper", "cheese", "egg", "avocado", "lemon"),
            listOf("Chop everything into bite-sized pieces.", "Dress with oil, lemon, salt and pepper and toss.")),
        Recipe("local:dal", "Comforting lentil dal", 35,
            listOf("1 cup lentils", "1 onion", "2 tomatoes", "1 tsp turmeric", "1 tsp cumin", "Garlic and ginger"),
            listOf("lentil", "dal", "onion", "tomato", "garlic", "ginger", "spinach"),
            listOf("Boil lentils with turmeric until soft.", "Fry cumin, onion, garlic and ginger; add tomatoes.", "Combine with the lentils, simmer 5 minutes and season.")),
        Recipe("local:potato-hash", "Crispy potato hash", 30,
            listOf("4 potatoes", "1 onion", "Leftover meat or sausage", "2 eggs"),
            listOf("potato", "onion", "sausage", "bacon", "ham", "egg", "pepper"),
            listOf("Par-boil diced potatoes 5 minutes, then drain.", "Fry with onion until crisp.", "Add chopped meat, then fry eggs on top.")),
        Recipe("local:yogurt-parfait", "Yogurt fruit parfait", 5,
            listOf("1 cup yogurt", "Fresh fruit", "Oats or granola", "Honey"),
            listOf("yogurt", "oats", "banana", "berr", "strawberr", "apple", "mango", "honey"),
            listOf("Layer yogurt, fruit and oats in a glass.", "Drizzle with honey and serve.")),
        Recipe("local:quesadilla", "Cheese & veg quesadillas", 15,
            listOf("Tortillas", "Grated cheese", "Beans, peppers or leftover chicken"),
            listOf("tortilla", "wrap", "cheese", "beans", "pepper", "chicken", "corn", "onion"),
            listOf("Fill half of each tortilla with cheese and fillings.", "Fold and dry-fry until crisp on both sides.", "Cut into wedges.")),
        Recipe("local:fish-bake", "Lemon herb fish bake", 25,
            listOf("2 fish fillets", "1 lemon", "Garlic", "Tomatoes", "Olive oil"),
            listOf("fish", "salmon", "cod", "lemon", "tomato", "garlic", "potato"),
            listOf("Heat oven to 200°C.", "Lay fish on tomatoes, add garlic, lemon slices and oil.", "Bake 15–18 minutes until the fish flakes.")),
        Recipe("local:apple-crumble", "Bruised-apple crumble", 45,
            listOf("4 apples", "100 g flour", "75 g butter", "75 g sugar", "1 tsp cinnamon"),
            listOf("apple", "pear", "butter", "flour", "oats", "sugar"),
            listOf("Slice fruit into a dish with cinnamon.", "Rub flour, butter and sugar into crumbs.", "Scatter over and bake at 180°C for 30 minutes.")),
        Recipe("local:shakshuka", "One-pan shakshuka", 25,
            listOf("4 eggs", "1 can or 4 fresh tomatoes", "1 onion", "1 pepper", "Cumin and paprika"),
            listOf("egg", "tomato", "onion", "pepper", "garlic"),
            listOf("Soften onion and pepper with the spices.", "Add tomatoes and simmer to a sauce.", "Make wells, crack in eggs, cover and cook until set.")),
    )
}
