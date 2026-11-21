package com.pantrypulse.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pantrypulse.app.MainActivity
import com.pantrypulse.app.PantryPulseApp
import com.pantrypulse.app.R
import com.pantrypulse.app.data.community.CommunityEvent
import java.util.concurrent.TimeUnit

/** Polls the community backend for new activity (claims, requests, approvals) and notifies. */
class CommunityEventsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PantryPulseApp).container
        if (!container.community.current().connected) return Result.success()
        return try {
            // Alerts on: keep the server's approximate location fresh so "nearest" stays accurate as you move.
            if (container.community.current().user?.notifyRadiusKm?.let { it > 0.0 } == true &&
                container.location.hasPermission(applicationContext)) {
                runCatching { container.location.current(applicationContext) }.getOrNull()
                    ?.let { runCatching { container.community.refreshAlertLocation(it.lat, it.lon) } }
            }
            notify(applicationContext, container.community.newEvents())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val CHANNEL_ID = "community"
        private const val WORK = "pantrypulse_community_events"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Giveaways", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "New giveaways near you, plus claims, requests and pickups for your giveaways" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CommunityEventsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun notify(context: Context, events: List<CommunityEvent>) {
            if (events.isEmpty() || !ExpiryNotifier.canNotify(context)) return
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(ExpiryNotifier.EXTRA_OPEN, "giveaways")
            }
            val pending = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val title = when {
                events.all { it.type == "nearby" } -> if (events.size == 1) "Free food nearby" else "${events.size} giveaways near you"
                events.size == 1 -> "Giveaway update"
                else -> "${events.size} giveaway updates"
            }
            val body = events.takeLast(5).joinToString("\n") { it.message }
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFFFF5A2C.toInt())
                .setContentTitle(title)
                .setContentText(events.last().message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            try { NotificationManagerCompat.from(context).notify(4202, n) } catch (_: SecurityException) {}
        }
    }
}
