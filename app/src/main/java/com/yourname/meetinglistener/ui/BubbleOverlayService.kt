package com.yourname.meetinglistener.ui

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.yourname.meetinglistener.R
import com.yourname.meetinglistener.databinding.BubbleOverlayBinding

/**
 * BubbleOverlayService.kt
 *
 * PURPOSE:
 * Creates a floating bubble that appears over all apps
 * Allows user to ask questions without leaving meeting app
 *
 * FEATURES:
 * - Draggable bubble
 * - Tap to expand into question input
 * - Stays on top of other apps
 * - Dismissible
 *
 * USAGE:
 * Start: startService(Intent(context, BubbleOverlayService::class.java))
 * Stop: Send ACTION_STOP intent
 */
class BubbleOverlayService : Service() {

    // ----------------------------------------
    // Constants
    // ----------------------------------------
    private val TAG = "BubbleOverlay"

    companion object {
        const val ACTION_SHOW = "SHOW_BUBBLE"
        const val ACTION_HIDE = "HIDE_BUBBLE"
    }

    // ----------------------------------------
    // System Services
    // ----------------------------------------
    private lateinit var windowManager: WindowManager

    // ----------------------------------------
    // Views
    // ----------------------------------------
    private var bubbleView: View? = null
    private var expandedView: View? = null

    // ----------------------------------------
    // Touch / Position Tracking
    // ----------------------------------------
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // ----------------------------------------
    // Lifecycle
    // ----------------------------------------
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showBubble()
            ACTION_HIDE -> hideBubble()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        hideBubble()
    }

    // ----------------------------------------
    // Bubble Control
    // ----------------------------------------
    private fun showBubble() {
        if (bubbleView != null) return

        bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_overlay, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = windowManager.defaultDisplay.width - 100
        params.y = windowManager.defaultDisplay.height / 2

        bubbleView?.setOnTouchListener(bubbleTouchListener)

        windowManager.addView(bubbleView, params)
        Log.d(TAG, "Bubble shown")
    }

    private fun hideBubble() {
        bubbleView?.let {
            windowManager.removeView(it)
            bubbleView = null
        }

        expandedView?.let {
            windowManager.removeView(it)
            expandedView = null
        }

        Log.d(TAG, "Bubble hidden")
        stopSelf()
    }

    // ----------------------------------------
    // Touch Handling
    // ----------------------------------------
    private val bubbleTouchListener = View.OnTouchListener { view, event ->
        val params = view.layoutParams as WindowManager.LayoutParams

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                true
            }

            MotionEvent.ACTION_MOVE -> {
                params.x = initialX + (event.rawX - initialTouchX).toInt()
                params.y = initialY + (event.rawY - initialTouchY).toInt()
                windowManager.updateViewLayout(view, params)
                true
            }

            MotionEvent.ACTION_UP -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY
                val distance =
                    Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble())

                if (distance < 20) {
                    showExpandedView()
                }
                true
            }

            else -> false
        }
    }

    // ----------------------------------------
    // Expanded View
    // ----------------------------------------
    private fun showExpandedView() {
        if (expandedView != null) return

        // TODO: Create expanded view with question input
        // This will be a larger overlay with text input and submit button
        Log.d(TAG, "TODO: Show expanded question input")
    }
}
