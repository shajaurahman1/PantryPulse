package com.pantrypulse.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pantrypulse.app.PantryPulseApp
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Runs once a day and notifies about food that expires within the user's warning window. */
class ExpiryCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PantryPulseApp).container
        val session = container.session.current()
        if (!session.notificationsEnabled) return Result.success()
        val householdId = session.householdId ?: return Result.success()
        val items = container.pantry.expiringWithin(householdId, session.warningDays)
        ExpiryNotifier.show(applicationContext, items)
        return Result.success()
    }

    companion object {
        private const val DAILY = "pantrypulse_daily_expiry_check"
        private const val NOW = "pantrypulse_expiry_check_now"

        /** Schedules the daily check for about 9am local time. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.withHour(9).withMinute(0).withSecond(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ExpiryCheckWorker>().build())
        }
    }
}
