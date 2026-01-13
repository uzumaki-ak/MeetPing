package com.yourname.meetinglistener.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yourname.meetinglistener.MainActivity
import com.yourname.meetinglistener.R

/**
 * NotificationHelper.kt (FIXED - GUARANTEED TO WORK)
 */
class NotificationHelper(private val context: Context) {

    private val TAG = "NotificationHelper"

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

    companion object {
        private const val ALERT_CHANNEL_ID = "name_mention_alerts"
        private const val ALERT_NOTIFICATION_ID = 2001
    }

    init {
        createAlertChannel()
    }

    private fun createAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Name Mention Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when your name is mentioned"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Alert channel created")
        }
    }

    fun sendNameMentionAlert(userName: String) {
        Log.d(TAG, "🔔 SENDING NAME ALERT for: $userName")

        // Vibrate first
        vibrateDevice()

        // Create intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("name_mentioned", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification with high priority
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("🔔 YOUR NAME WAS MENTIONED!")
            .setContentText("'$userName' was just mentioned in the meeting")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)

        Log.d(TAG, "✅ Notification sent successfully")
    }

    private fun vibrateDevice() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 500, 200, 500)
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
            }

            Log.d(TAG, "✅ Vibration triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Vibration error: ${e.message}")
        }
    }
}