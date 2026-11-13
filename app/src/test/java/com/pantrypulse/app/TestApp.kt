package com.pantrypulse.app

import com.pantrypulse.app.data.db.AppDatabase

/** Application used by Robolectric tests: in-memory database, no WorkManager scheduling. */
class TestApp : PantryPulseApp() {
    override fun createContainer(): AppContainer = AppContainer(this, AppDatabase.inMemory(this))
    override fun scheduleBackgroundWork() = Unit
}
