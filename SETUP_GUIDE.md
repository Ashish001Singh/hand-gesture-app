# HandsFree Control - Setup & Build Guide

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- JDK 17
- Physical Android device (Android 10+) with front camera
- USB debugging enabled

## Step 1: Download the MediaPipe Model

The app requires the `hand_landmarker.task` model file from Google MediaPipe.

1. Download from: https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
2. Place the file in: `app/src/main/assets/hand_landmarker.task`

```bash
mkdir -p app/src/main/assets
curl -o app/src/main/assets/hand_landmarker.task \
  https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
```

## Step 2: Open in Android Studio

1. Open Android Studio
2. File → Open → Select the `HandsFreeControl` folder
3. Wait for Gradle sync to complete

## Step 3: Build & Install

1. Connect your Android device via USB
2. Select your device in the toolbar device dropdown
3. Click Run (green play button) or press Shift+F10
4. The app will build and install on your device

## Step 4: Grant Permissions

### Camera Permission
- The app will request camera permission on first launch
- Tap "Allow" to grant access to the front camera

### Accessibility Service (REQUIRED)
This is critical — without it, gestures won't perform any actions.

1. Open the app and tap "Enable" on the warning card, OR
2. Go to Settings → Accessibility → Installed Services
3. Find "HandsFree Control"
4. Toggle it ON
5. Confirm the permission dialog

## Step 5: Using the App

1. Hold your phone so the front camera can see your hand
2. Keep your hand 30-60cm from the camera
3. Make gestures clearly and hold them briefly

### Supported Gestures

| Gesture | How To | Action |
|---------|--------|--------|
| Index Finger Up | Point index finger upward, curl other fingers | Scroll up |
| Index Finger Down | Point index finger downward (hand inverted) | Scroll down |
| Swipe Right | Move open hand to the right | Next content |
| Swipe Left | Move open hand to the left | Previous content |
| Fist | Close all fingers into a fist | Play/Pause video |
| Thumbs Up | Thumb up, all others curled | Like content |
| Two Fingers (Peace) | Index + middle finger up, others curled | Open YouTube |

## Project Structure

```
HandsFreeControl/
├── app/
│   ├── build.gradle.kts          # Dependencies & build config
│   └── src/main/
│       ├── AndroidManifest.xml    # Permissions & service declaration
│       ├── assets/
│       │   └── hand_landmarker.task  # MediaPipe model (download separately)
│       ├── res/
│       │   ├── values/
│       │   │   ├── strings.xml
│       │   │   ├── colors.xml
│       │   │   └── themes.xml
│       │   └── xml/
│       │       └── accessibility_service_config.xml
│       └── java/com/handsfree/control/
│           ├── MainActivity.kt           # App entry point
│           ├── camera/
│           │   ├── CameraManager.kt      # CameraX setup & lifecycle
│           │   └── FrameAnalyzer.kt      # Frame callback interface
│           ├── detection/
│           │   ├── HandDetector.kt       # MediaPipe hand landmark detection
│           │   └── HandDetectionListener.kt
│           ├── gesture/
│           │   ├── GestureRecognizer.kt  # Static pose classification
│           │   ├── SwipeDetector.kt      # Motion-based swipe detection
│           │   └── GestureProcessor.kt   # Debouncing & confirmation
│           ├── mapping/
│           │   └── GestureActionMapper.kt # Gesture → DeviceAction
│           ├── accessibility/
│           │   ├── GestureAccessibilityService.kt  # System gesture dispatch
│           │   └── ActionExecutor.kt     # Bridge to accessibility service
│           ├── data/
│           │   ├── model/
│           │   │   ├── HandGesture.kt        # Gesture enum
│           │   │   ├── DeviceAction.kt       # Action sealed class
│           │   │   ├── HandLandmarkPoint.kt  # Landmark data classes
│           │   │   └── GestureSettings.kt    # User preferences
│           │   └── repository/
│           │       └── SettingsRepository.kt # DataStore persistence
│           └── ui/
│               ├── theme/
│               │   └── Theme.kt
│               ├── components/
│               │   ├── CameraPreview.kt     # CameraX PreviewView wrapper
│               │   ├── GestureOverlay.kt    # Hand skeleton visualization
│               │   └── GestureStatusBar.kt  # Current gesture display
│               ├── screens/
│               │   ├── MainScreen.kt        # Main camera + controls
│               │   └── SettingsScreen.kt    # Gesture customization
│               └── viewmodel/
│                   └── MainViewModel.kt     # MVVM state management
├── build.gradle.kts              # Root build file
├── settings.gradle.kts           # Module settings
├── gradle.properties             # Build properties
└── gradle/wrapper/
    └── gradle-wrapper.properties # Gradle version
```

## Performance Notes

- **30 FPS Processing**: CameraX delivers frames at camera rate; `STRATEGY_KEEP_ONLY_LATEST` drops frames if detection takes longer than one interval, preventing backlog.
- **Background Threading**: All MediaPipe inference runs on a dedicated single-thread executor, never touching the main/UI thread.
- **GPU Acceleration**: MediaPipe uses the GPU delegate by default, falling back to CPU if unavailable.
- **Memory Efficiency**: Bitmaps are recycled after each frame. ImageProxy is always closed to free the camera buffer.
- **Battery Optimization**: 640x480 resolution balances accuracy with power consumption. Detection only runs when enabled.
- **Debouncing**: 3-frame stability requirement + configurable cooldown prevents jittery rapid-fire actions.

## Troubleshooting

**Gestures detected but no action happens:**
→ Enable the Accessibility Service in Settings → Accessibility

**Hand not detected:**
→ Ensure good lighting
→ Keep hand 30-60cm from camera
→ Try adjusting sensitivity in Settings

**App crashes on startup:**
→ Verify `hand_landmarker.task` is in the assets folder
→ Check that min SDK is 29+ in your device

**Laggy detection:**
→ Lower sensitivity in Settings
→ Increase cooldown time
→ Ensure no other camera-intensive apps are running
