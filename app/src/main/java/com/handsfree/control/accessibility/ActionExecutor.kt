package com.handsfree.control.accessibility

import android.util.Log
import com.handsfree.control.data.model.DeviceAction

/**
 * ActionExecutor bridges the gesture pipeline with the Accessibility Service.
 *
 * It checks whether the Accessibility Service is connected before attempting
 * to dispatch actions, and provides status feedback.
 */
class ActionExecutor {

    companion object {
        private const val TAG = "ActionExecutor"
    }

    /** Whether the Accessibility Service is currently connected. */
    val isServiceConnected: Boolean
        get() = GestureAccessibilityService.instance != null

    /**
     * Execute a device action via the Accessibility Service.
     *
     * @param action The action to execute
     * @return true if the action was dispatched, false if the service is unavailable
     */
    fun execute(action: DeviceAction): Boolean {
        if (action is DeviceAction.NoAction) return true

        val service = GestureAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "Cannot execute action: Accessibility Service not connected. " +
                    "Please enable it in Settings → Accessibility → HandsFree Control")
            return false
        }

        service.executeAction(action)
        return true
    }
}
