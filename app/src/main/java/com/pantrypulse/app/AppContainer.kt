package com.pantrypulse.app

import android.content.Context
import com.pantrypulse.app.data.db.AppDatabase
import com.pantrypulse.app.data.remote.FoodRescueClient
import com.pantrypulse.app.data.remote.MealDbClient
import com.pantrypulse.app.data.remote.OpenFoodFactsClient
import com.pantrypulse.app.data.repo.AuthRepository
import com.pantrypulse.app.data.repo.HouseholdRepository
import com.pantrypulse.app.data.repo.PantryRepository
import com.pantrypulse.app.data.repo.SessionStore
import com.pantrypulse.app.data.repo.ShoppingRepository

/** Simple manual dependency container shared by the whole app. */
class AppContainer(context: Context, val db: AppDatabase = AppDatabase.build(context)) {
    val session = SessionStore(context)
    val households = HouseholdRepository(db, session)
    val auth = AuthRepository(db, session, households)
    val pantry = PantryRepository(db)
    val shopping = ShoppingRepository(db)
    val openFoodFacts = OpenFoodFactsClient()
    val mealDb = MealDbClient()
    val foodRescue = FoodRescueClient()
}
