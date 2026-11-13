package com.pantrypulse.app

import com.pantrypulse.app.data.db.AppDatabase

/** Application used by Robolectric tests: in-memory database, no WorkManager scheduling. */
class TestApp : PantryPulseApp() {
    val fakeCommunity = FakeCommunityApi()
    override fun createContainer(): AppContainer = AppContainer(this, AppDatabase.inMemory(this), fakeCommunity, FixedLocation())
    override fun scheduleBackgroundWork() = Unit
}
