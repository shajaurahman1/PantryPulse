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

        /** "9 am", "12 pm", "6 pm". */
        fun hourLabel(hour: Int) = "${if (hour % 12 == 0) 12 else hour % 12} ${if (hour < 12) "am" else "pm"}"

        /** Schedules the daily check for about [hour]:00 local time. [replace] restarts it when the time is changed. */
        fun schedule(context: Context, hour: Int = 9, replace: Boolean = false) {
            val now = LocalDateTime.now()
            var next = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY, if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ExpiryCheckWorker>().build())
        }
    }
}
