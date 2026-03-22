package com.handsfree.control.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handsfree.control.accessibility.ActionExecutor
import com.handsfree.control.data.model.DeviceAction
import com.handsfree.control.data.model.GestureSettings
import com.handsfree.control.data.model.HandGesture
import com.handsfree.control.data.model.HandLandmarks
import com.handsfree.control.data.repository.SettingsRepository
import com.handsfree.control.detection.HandDetectionListener
import com.handsfree.control.gesture.GestureCallback
import com.handsfree.control.gesture.GestureProcessor
import com.handsfree.control.mapping.GestureActionMapper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * MainViewModel orchestrates the entire gesture control pipeline.
 *
 * ## Data Flow
 * Camera Frame → HandDetector → HandDetectionListener → GestureProcessor
 * → GestureCallback → GestureActionMapper → ActionExecutor → System Gesture
 *
 * The ViewModel exposes UI state via StateFlows for the Compose UI to observe.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // ── Dependencies ──
    private val settingsRepository = SettingsRepository(application)
    private val actionExecutor = ActionExecutor()
    private val gestureMapper = GestureActionMapper()

    // ── Gesture Processing ──
    private val gestureProcessor = GestureProcessor(
        callback = object : GestureCallback {
            override fun onGestureDetected(gesture: HandGesture, confidence: Float) {
                _currentGesture.value = gesture
                _gestureConfidence.value = confidence
            }

            override fun onGestureConfirmed(gesture: HandGesture, confidence: Float) {
                // Map gesture to action and execute
                val action = gestureMapper.mapGestureToAction(gesture)
                val success = actionExecutor.execute(action)

                _lastAction.value = if (success) action else DeviceAction.NoAction
                _actionHistory.value = (_actionHistory.value + GestureEvent(
                    gesture = gesture,
                    action = action,
                    timestamp = System.currentTimeMillis(),
                    success = success
                )).takeLast(20)  // Keep last 20 events

                Log.d(TAG, "Action executed: $action (success=$success)")
            }
        }
    )

    // ── UI State ──

    /** Currently detected gesture (updated every frame). */
    private val _currentGesture = MutableStateFlow(HandGesture.NONE)
    val currentGesture: StateFlow<HandGesture> = _currentGesture.asStateFlow()

    /** Confidence level of the current detection (0.0 to 1.0). */
    private val _gestureConfidence = MutableStateFlow(0f)
    val gestureConfidence: StateFlow<Float> = _gestureConfidence.asStateFlow()

    /** The most recent action executed. */
    private val _lastAction = MutableStateFlow<DeviceAction>(DeviceAction.NoAction)
    val lastAction: StateFlow<DeviceAction> = _lastAction.asStateFlow()

    /** Whether the accessibility service is connected. */
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    /** Whether gesture detection is active. */
    private val _isDetectionActive = MutableStateFlow(false)
    val isDetectionActive: StateFlow<Boolean> = _isDetectionActive.asStateFlow()

    /** Current hand landmarks for overlay rendering. */
    private val _handLandmarks = MutableStateFlow<List<HandLandmarks>>(emptyList())
    val handLandmarks: StateFlow<List<HandLandmarks>> = _handLandmarks.asStateFlow()

    /** Gesture event history for the activity log. */
    private val _actionHistory = MutableStateFlow<List<GestureEvent>>(emptyList())
    val actionHistory: StateFlow<List<GestureEvent>> = _actionHistory.asStateFlow()

    /** User settings. */
    val settings: StateFlow<GestureSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GestureSettings())

    init {
        // Observe settings changes and propagate to gesture pipeline
        viewModelScope.launch {
            settings.collect { newSettings ->
                gestureProcessor.updateSettings(newSettings)
                gestureMapper.updateSettings(newSettings)
                _isDetectionActive.value = newSettings.isEnabled
            }
        }
    }

    /**
     * HandDetectionListener that feeds detected landmarks into the gesture pipeline.
     * This is passed to the HandDetector.
     */
    val handDetectionListener = HandDetectionListener { hands ->
        _handLandmarks.value = hands
        gestureProcessor.processHands(hands)
    }

    /** Check and update accessibility service connection status. */
    fun refreshServiceStatus() {
        _isServiceConnected.value = actionExecutor.isServiceConnected
    }

    /** Toggle gesture detection on/off. */
    fun toggleDetection() {
        viewModelScope.launch {
            val current = settings.value
            settingsRepository.setEnabled(!current.isEnabled)
        }
    }

    /** Update gesture settings. */
    fun updateSettings(settings: GestureSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings)
        }
    }
}

/**
 * Represents a single gesture event in the history log.
 */
data class GestureEvent(
    val gesture: HandGesture,
    val action: DeviceAction,
    val timestamp: Long,
    val success: Boolean
)
