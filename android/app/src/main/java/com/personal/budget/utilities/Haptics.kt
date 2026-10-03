package com.personal.budget.utilities

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Subtle haptics for meaningful moments only (save, closing a month, settling an IOU).
 * Uses View haptic constants, which respect the system "touch feedback" setting and need
 * no VIBRATE permission.
 */
object Haptics {
    fun confirm(view: View) {
        val c = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        view.performHapticFeedback(c)
    }

    fun toggle(view: View, on: Boolean) {
        val c = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(c)
    }

    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
