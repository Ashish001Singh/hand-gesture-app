#!/usr/bin/env bash
# ╔══════════════════════════════════════════════════════════════════╗
# ║        HandsFree Control — Automated APK Builder                ║
# ║        Works on: macOS (Intel + Apple Silicon), Linux x86_64    ║
# ╚══════════════════════════════════════════════════════════════════╝
#
# USAGE:
#   chmod +x build_apk.sh
#   ./build_apk.sh
#
# PREREQUISITES:
#   - Java 17+ installed (https://adoptium.net)
#   - Internet connection (downloads Android SDK + dependencies)
#
# The script will:
#   1. Check for Java 17+
#   2. Download the MediaPipe hand landmark model
#   3. Download Android SDK command line tools
#   4. Install required SDK packages
#   5. Build the debug APK
#   6. Tell you exactly where the APK is

set -e  # Exit on any error

# ── Colors ──
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║       HandsFree Control APK Builder                     ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# ── Step 1: Check Java 17+ ──
log_info "Checking Java version..."
if ! command -v java &>/dev/null; then
    log_error "Java not found!"
    echo ""
    echo "Please install Java 17 from: https://adoptium.net"
    echo "Then re-run this script."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}' | awk -F'.' '{print $1}')
if [ "$JAVA_VERSION" -lt 17 ] 2>/dev/null; then
    log_error "Java 17+ required. You have Java $JAVA_VERSION."
    echo "Download Java 17 from: https://adoptium.net"
    exit 1
fi
log_success "Java $JAVA_VERSION found"

# ── Step 2: Detect OS ──
OS_TYPE=""
SDK_URL=""
if [[ "$OSTYPE" == "darwin"* ]]; then
    OS_TYPE="mac"
    # macOS — use Mac version of cmdline tools
    ARCH=$(uname -m)
    SDK_URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
    log_info "Detected: macOS ($ARCH)"
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    OS_TYPE="linux"
    SDK_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    log_info "Detected: Linux"
else
    log_error "Unsupported OS. Please use macOS or Linux, or see SETUP_GUIDE.md."
    exit 1
fi

# ── Step 3: Set up directories ──
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="$SCRIPT_DIR/.android-sdk"
ASSETS_DIR="$SCRIPT_DIR/app/src/main/assets"

mkdir -p "$SDK_DIR"
mkdir -p "$ASSETS_DIR"

# ── Step 4: Download MediaPipe model ──
MODEL_PATH="$ASSETS_DIR/hand_landmarker.task"
if [ -f "$MODEL_PATH" ]; then
    log_success "MediaPipe model already exists, skipping download"
else
    log_info "Downloading MediaPipe hand_landmarker model (~25MB)..."
    MEDIAPIPE_URL="https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"
    if curl -L --progress-bar -o "$MODEL_PATH" "$MEDIAPIPE_URL"; then
        log_success "MediaPipe model downloaded"
    else
        log_error "Failed to download MediaPipe model"
        log_error "Try manually downloading from: $MEDIAPIPE_URL"
        log_error "And place it at: $MODEL_PATH"
        exit 1
    fi
fi

# ── Step 5: Download Android SDK command line tools ──
CMDLINE_TOOLS_DIR="$SDK_DIR/cmdline-tools"
if [ -d "$CMDLINE_TOOLS_DIR/latest/bin" ]; then
    log_success "Android cmdline-tools already installed"
else
    log_info "Downloading Android SDK command line tools (~150MB)..."
    ZIP_FILE="$SDK_DIR/cmdline-tools.zip"

    if curl -L --progress-bar -o "$ZIP_FILE" "$SDK_URL"; then
        log_info "Extracting SDK tools..."
        mkdir -p "$CMDLINE_TOOLS_DIR"
        unzip -q "$ZIP_FILE" -d "$CMDLINE_TOOLS_DIR"
        # Rename to 'latest' as required by sdkmanager
        mv "$CMDLINE_TOOLS_DIR/cmdline-tools" "$CMDLINE_TOOLS_DIR/latest" 2>/dev/null || true
        rm "$ZIP_FILE"
        log_success "Android SDK tools extracted"
    else
        log_error "Failed to download Android SDK tools"
        exit 1
    fi
fi

SDKMANAGER="$CMDLINE_TOOLS_DIR/latest/bin/sdkmanager"
chmod +x "$SDKMANAGER"

# ── Step 6: Accept licenses & install required SDK packages ──
export ANDROID_SDK_ROOT="$SDK_DIR"
export ANDROID_HOME="$SDK_DIR"

log_info "Accepting Android SDK licenses..."
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses > /dev/null 2>&1 || true

log_info "Installing Android SDK packages (build-tools, platform)..."
log_info "This may take 5-10 minutes on first run..."
"$SDKMANAGER" --sdk_root="$SDK_DIR" \
    "build-tools;34.0.0" \
    "platforms;android-34" \
    "platform-tools" 2>&1 | grep -E "Installing|Unzipping|done|Error" || true

log_success "Android SDK packages installed"

# ── Step 7: Make Gradle wrapper executable ──
chmod +x "$SCRIPT_DIR/gradlew"

# ── Step 8: Set local.properties for SDK path ──
echo "sdk.dir=$SDK_DIR" > "$SCRIPT_DIR/local.properties"
log_success "local.properties configured"

# ── Step 9: Build the APK ──
log_info "Building HandsFree Control debug APK..."
log_info "This will download Gradle and Maven dependencies (~500MB first time)..."
echo ""

cd "$SCRIPT_DIR"
./gradlew assembleDebug \
    -Dorg.gradle.jvmargs="-Xmx2g" \
    --no-daemon \
    2>&1 | grep -E "BUILD|FAILURE|error:|warning:|Downloading|Download|Task|APK" | head -100

echo ""

# ── Step 10: Find and report APK ──
APK_PATH=$(find "$SCRIPT_DIR" -name "*.apk" -path "*/debug/*" | head -1)

if [ -n "$APK_PATH" ]; then
    echo ""
    echo "╔══════════════════════════════════════════════════════════╗"
    echo "║                  ✅ BUILD SUCCESSFUL!                   ║"
    echo "╚══════════════════════════════════════════════════════════╝"
    echo ""
    log_success "APK built: $APK_PATH"
    echo ""
    echo "📱 To install on your Android phone:"
    echo ""
    echo "   Option A — USB install (fastest):"
    echo "   adb install \"$APK_PATH\""
    echo ""
    echo "   Option B — Manual install:"
    echo "   1. Copy the APK to your phone"
    echo "   2. On your phone: Settings → Security → Enable 'Install Unknown Apps'"
    echo "   3. Open the APK file with your file manager"
    echo ""

    # Copy APK to easy-to-find location
    DEST="$SCRIPT_DIR/HandsFreeControl-debug.apk"
    cp "$APK_PATH" "$DEST"
    log_success "APK also copied to: $DEST"
else
    echo ""
    log_error "Build failed — APK not found"
    echo "Check the output above for error details."
    echo "Common fixes:"
    echo "  - Make sure Java 17+ is your active JDK"
    echo "  - Check you have ~3GB free disk space"
    echo "  - Try running: ./gradlew assembleDebug --stacktrace"
    exit 1
fi
