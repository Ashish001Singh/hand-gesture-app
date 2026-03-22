package com.handsfree.control.ui.screens

/**
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║                    HANDSFREE CONTROL ARCHITECTURE                   ║
 * ╠══════════════════════════════════════════════════════════════════════╣
 * ║                                                                     ║
 * ║   ┌─────────────┐     ┌──────────────────┐     ┌───────────────┐   ║
 * ║   │   CAMERA     │────▶│  HAND DETECTION   │────▶│   GESTURE     │   ║
 * ║   │   MODULE     │     │  MODULE           │     │  RECOGNITION  │   ║
 * ║   │             │     │                   │     │   MODULE      │   ║
 * ║   │ CameraX     │     │ MediaPipe Hands   │     │              │   ║
 * ║   │ Front Cam   │     │ 21 Landmarks      │     │ Static Poses │   ║
 * ║   │ 640x480     │     │ GPU Accelerated   │     │ + Swipes     │   ║
 * ║   └─────────────┘     └──────────────────┘     └──────┬────────┘   ║
 * ║                                                        │            ║
 * ║                                                        ▼            ║
 * ║   ┌─────────────┐     ┌──────────────────┐     ┌───────────────┐   ║
 * ║   │   UI         │◀────│  VIEWMODEL        │◀────│   GESTURE     │   ║
 * ║   │   MODULE     │     │  (MVVM)           │     │  MAPPING      │   ║
 * ║   │             │     │                   │     │   ENGINE      │   ║
 * ║   │ Compose UI  │     │ State Flows       │     │              │   ║
 * ║   │ Camera View │     │ Settings          │     │ Gesture →    │   ║
 * ║   │ Overlay     │     │ Action History    │     │ DeviceAction │   ║
 * ║   └─────────────┘     └──────────────────┘     └──────┬────────┘   ║
 * ║                                                        │            ║
 * ║                                                        ▼            ║
 * ║                                                ┌───────────────┐   ║
 * ║                                                │ ACCESSIBILITY │   ║
 * ║                                                │ SERVICE       │   ║
 * ║                                                │               │   ║
 * ║                                                │ dispatchGes() │   ║
 * ║                                                │ Scroll/Swipe  │   ║
 * ║                                                │ Tap/Launch    │   ║
 * ║                                                └───────────────┘   ║
 * ║                                                        │            ║
 * ║                                                        ▼            ║
 * ║                                                ┌───────────────┐   ║
 * ║                                                │ ANDROID OS    │   ║
 * ║                                                │ (All Apps)    │   ║
 * ║                                                └───────────────┘   ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 *
 * DATA FLOW:
 * Camera Frame (30fps)
 *   → ImageProxy (RGBA_8888, 640x480)
 *   → Bitmap → Mirror → MediaPipe MPImage
 *   → HandLandmarkerResult (21 landmarks × 3D)
 *   → HandLandmarks (domain model)
 *   → GestureRecognizer (static pose classification)
 *   → SwipeDetector (motion tracking across frames)
 *   → GestureProcessor (debounce + cooldown)
 *   → GestureActionMapper (gesture → DeviceAction)
 *   → ActionExecutor → AccessibilityService
 *   → dispatchGesture() → System performs touch
 */
internal object ArchitectureDiagram
