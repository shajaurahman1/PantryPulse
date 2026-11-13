package com.pantrypulse.app

import android.app.Application
import com.pantrypulse.app.notifications.CommunityEventsWorker
import com.pantrypulse.app.notifications.ExpiryCheckWorker
import com.pantrypulse.app.notifications.ExpiryNotifier
import org.osmdroid.config.Configuration

open class PantryPulseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        ExpiryNotifier.createChannel(this)
        CommunityEventsWorker.createChannel(this)
        Configuration.getInstance().userAgentValue = "PantryPulse-Android/${BuildConfig.VERSION_NAME}"
        scheduleBackgroundWork()
    }

    /** Tests override these to use an in-memory database and skip WorkManager. */
    protected open fun createContainer(): AppContainer = AppContainer(this)
    protected open fun scheduleBackgroundWork() {
        ExpiryCheckWorker.schedule(this)
        CommunityEventsWorker.schedule(this)
    }
}
