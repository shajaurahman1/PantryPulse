package com.pantrypulse.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pantrypulse.app.MainActivity
import com.pantrypulse.app.R
import com.pantrypulse.app.data.db.ProductEntity
import com.pantrypulse.app.domain.Expiry

object ExpiryNotifier {
    const val CHANNEL_ID = "expiry_alerts"
    private const val NOTIFICATION_ID = 4201
    const val EXTRA_OPEN = "open_destination"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_expiry_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_expiry_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** True only when the system will really show our notifications (permission granted and not switched off in settings). */
    fun enabledInSystem(context: Context): Boolean = canNotify(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Opens this app's notification page in the phone's settings. */
    fun openSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Sends a sample notification so the person can confirm reminders reach their phone. Returns false if blocked. */
    fun showTest(context: Context): Boolean {
        if (!enabledInSystem(context)) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN, "pantry")
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1F7A57.toInt())
            .setContentTitle("Expiry reminders are on ✓")
            .setContentText("You'll be told here when food in your pantry is about to expire.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try { NotificationManagerCompat.from(context).notify(4203, notification); true } catch (_: SecurityException) { false }
    }

    /** Builds the title and body; split out so it can be unit tested. */
    fun content(items: List<ProductEntity>): Pair<String, String> {
        val expired = items.count { (Expiry.daysLeft(it.expiryDate) ?: 1) < 0 }
        val soon = items.size - expired
        val title = when {
            expired > 0 && soon > 0 -> "$soon item${if (soon > 1) "s" else ""} expiring soon, $expired expired"
            expired > 0 -> "$expired item${if (expired > 1) "s have" else " has"} expired"
            else -> "$soon item${if (soon > 1) "s" else ""} expiring soon"
        }
        val body = items.take(5).joinToString(" • ") { "${it.name} (${Expiry.label(it.expiryDate).lowercase()})" } +
            if (items.size > 5) " and ${items.size - 5} more" else ""
        return title to body
    }

    fun show(context: Context, items: List<ProductEntity>) {
        if (items.isEmpty() || !canNotify(context)) return
        val (title, body) = content(items)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN, "pantry")
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1F7A57.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body + "\n\nOpen PantryPulse for recipe ideas or to donate food you won't use."))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }
}
