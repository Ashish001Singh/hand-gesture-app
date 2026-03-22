@echo off
REM ╔══════════════════════════════════════════════════════════════════╗
REM ║       HandsFree Control — Automated APK Builder (Windows)       ║
REM ╚══════════════════════════════════════════════════════════════════╝
REM
REM USAGE: Double-click this file, or run from Command Prompt
REM
REM PREREQUISITES:
REM   - Java 17+ installed (https://adoptium.net)
REM   - Internet connection

echo.
echo ╔══════════════════════════════════════════════════════════╗
echo ║       HandsFree Control APK Builder (Windows)           ║
echo ╚══════════════════════════════════════════════════════════╝
echo.

REM ── Check Java ──
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found!
    echo.
    echo Please install Java 17 from: https://adoptium.net
    echo Then re-run this script.
    pause
    exit /b 1
)
echo [OK] Java found

REM ── Set paths ──
set SCRIPT_DIR=%~dp0
set SDK_DIR=%SCRIPT_DIR%.android-sdk
set ASSETS_DIR=%SCRIPT_DIR%app\src\main\assets

mkdir "%SDK_DIR%" 2>nul
mkdir "%ASSETS_DIR%" 2>nul

REM ── Download MediaPipe model ──
if exist "%ASSETS_DIR%\hand_landmarker.task" (
    echo [OK] MediaPipe model already exists
) else (
    echo [INFO] Downloading MediaPipe model ^(~25MB^)...
    curl -L -o "%ASSETS_DIR%\hand_landmarker.task" "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"
    if errorlevel 1 (
        echo [ERROR] Failed to download MediaPipe model
        pause
        exit /b 1
    )
    echo [OK] MediaPipe model downloaded
)

REM ── Download Android SDK cmdline-tools ──
if exist "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" (
    echo [OK] Android SDK tools already installed
) else (
    echo [INFO] Downloading Android SDK tools ^(~150MB^)...
    curl -L -o "%SDK_DIR%\cmdline-tools.zip" "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    echo [INFO] Extracting...
    mkdir "%SDK_DIR%\cmdline-tools" 2>nul
    powershell -command "Expand-Archive -Path '%SDK_DIR%\cmdline-tools.zip' -DestinationPath '%SDK_DIR%\cmdline-tools' -Force"
    rename "%SDK_DIR%\cmdline-tools\cmdline-tools" "latest" 2>nul
    del "%SDK_DIR%\cmdline-tools.zip"
    echo [OK] SDK tools extracted
)

REM ── Accept licenses & install SDK packages ──
set ANDROID_SDK_ROOT=%SDK_DIR%
set ANDROID_HOME=%SDK_DIR%

echo [INFO] Accepting licenses and installing SDK packages...
echo y | "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%SDK_DIR%" --licenses >nul 2>&1
"%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%SDK_DIR%" "build-tools;34.0.0" "platforms;android-34" "platform-tools"
echo [OK] SDK packages installed

REM ── Write local.properties ──
echo sdk.dir=%SDK_DIR:\=/%> "%SCRIPT_DIR%local.properties"
echo [OK] local.properties written

REM ── Build APK ──
echo.
echo [INFO] Building APK... This downloads dependencies ^(~500MB first time^)
echo [INFO] Please wait - this can take 10-15 minutes...
echo.

cd /d "%SCRIPT_DIR%"
call gradlew.bat assembleDebug -Dorg.gradle.jvmargs="-Xmx2g" --no-daemon

REM ── Find APK ──
for /r "%SCRIPT_DIR%app\build\outputs\apk\debug" %%f in (*.apk) do set APK_PATH=%%f

if defined APK_PATH (
    echo.
    echo ╔══════════════════════════════════════════════════════════╗
    echo ║               BUILD SUCCESSFUL!                         ║
    echo ╚══════════════════════════════════════════════════════════╝
    echo.
    echo APK location: %APK_PATH%
    echo.
    copy "%APK_PATH%" "%SCRIPT_DIR%HandsFreeControl-debug.apk" >nul
    echo APK also copied to: %SCRIPT_DIR%HandsFreeControl-debug.apk
    echo.
    echo To install on your phone:
    echo   1. Connect phone via USB
    echo   2. Run: adb install "%SCRIPT_DIR%HandsFreeControl-debug.apk"
    echo   OR
    echo   1. Copy HandsFreeControl-debug.apk to your phone
    echo   2. Settings -^> Security -^> Enable Unknown Sources
    echo   3. Open the APK file on your phone
) else (
    echo [ERROR] Build failed. Run gradlew.bat assembleDebug --stacktrace for details
)

echo.
pause
