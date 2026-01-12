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
import androidx.core.app.NotificationCompat
import com.yourname.meetinglistener.MainActivity
import com.yourname.meetinglistener.R

/**
 * NotificationHelper.kt
 *
 * PURPOSE:
 * Manages all app notifications
 * Handles name mention alerts with sound and vibration
 * Creates notification channels
 *
 * FEATURES:
 * - Name mention alerts
 * - Custom vibration patterns
 * - High-priority notifications
 * - Action buttons in notifications
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

    companion object {
        private const val ALERT_CHANNEL_ID = "name_mention_alerts"
        private const val ALERT_NOTIFICATION_ID = 2001
    }

    init {
        createAlertChannel()
    }

    /**
     * Create notification channel for name mention alerts
     */
    private fun createAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Name Mention Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when your name is mentioned in meeting"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Send alert when user's name is mentioned
     * Includes vibration and high-priority notification
     */
    fun sendNameMentionAlert(userName: String) {
        // Vibrate device
        vibrateDevice()

        // Create intent to open app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("Your name was mentioned!")
            .setContentText("$userName was mentioned in the meeting")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    /**
     * Vibrate device with pattern
     */
    private fun vibrateDevice() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Pattern: wait 0ms, vibrate 250ms, wait 250ms, vibrate 250ms
            val pattern = longArrayOf(0, 250, 250, 250)
            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 250, 250, 250), -1)
        }
    }
}