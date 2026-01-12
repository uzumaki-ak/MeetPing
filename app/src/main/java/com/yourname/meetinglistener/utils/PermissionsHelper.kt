package com.yourname.meetinglistener.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PermissionsHelper.kt
 *
 * PURPOSE:
 * Manages all app permissions
 * Checks and requests necessary permissions
 *
 * REQUIRED PERMISSIONS:
 * - RECORD_AUDIO: For speech recognition
 * - POST_NOTIFICATIONS: For name mention alerts (Android 13+)
 * - SYSTEM_ALERT_WINDOW: For floating bubble overlay
 */
class PermissionsHelper(private val activity: Activity) {

    companion object {
        const val PERMISSION_REQUEST_CODE = 1001
        const val OVERLAY_PERMISSION_REQUEST_CODE = 1002

        // Required permissions list
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request all required permissions
     */
    fun requestPermissions() {
        ActivityCompat.requestPermissions(
            activity,
            REQUIRED_PERMISSIONS,
            PERMISSION_REQUEST_CODE
        )
    }

    /**
     * Check if RECORD_AUDIO permission is granted
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for older versions
        }
    }

    /**
     * Check if overlay permission is granted (for floating bubble)
     */
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else {
            true // Not required for older versions
        }
    }

    /**
     * Request overlay permission
     * Opens system settings for user to grant permission
     */
    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = android.content.Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    /**
     * Get list of missing permissions
     */
    fun getMissingPermissions(): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) !=
                    PackageManager.PERMISSION_GRANTED
        }
    }
}