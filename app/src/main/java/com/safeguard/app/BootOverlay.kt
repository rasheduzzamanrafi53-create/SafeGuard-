package com.safeguard.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

object BootOverlay {

    private const val SECONDS = 10

    fun show(context: Context) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return

        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val handler = Handler(Looper.getMainLooper())

        val view = TextView(app).apply {
            setBackgroundColor(Color.parseColor("#F2000000"))
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            isClickable = true
            isFocusable = true
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            return
        }

        var left = SECONDS
        val tick = object : Runnable {
            override fun run() {
                if (left <= 0) {
                    try {
                        wm.removeView(view)
                    } catch (e: Exception) {
                        // already removed
                    }
                    return
                }
                view.text = "SafeGuard is starting protection...\n\n$left"
                left -= 1
                handler.postDelayed(this, 1000L)
            }
        }
        handler.post(tick)
    }
}
