# How to Get the HandsFree Control APK

There are 3 ways to build the APK and install it on your phone.
**Choose the one that suits you best.**

---

## Method 1 — GitHub Actions (No setup needed, FREE) ✅ Recommended

This builds the APK entirely in the cloud. You just upload the code and download the APK.

### Steps:

**1. Create a free GitHub account** (if you don't have one)
→ https://github.com/signup

**2. Create a new repository**
→ Click the green "New" button on github.com
→ Name it `handsfree-control`
→ Set to Public
→ Click "Create repository"

**3. Upload the project**

Open Terminal (Mac/Linux) or Command Prompt (Windows) in the `HandsFreeControl` folder:

```bash
git init
git add .
git commit -m "Initial HandsFree Control project"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/handsfree-control.git
git push -u origin main
```

Replace `YOUR_USERNAME` with your GitHub username.

**4. Wait for the build (about 10 minutes)**
→ Go to your repository on GitHub
→ Click the **"Actions"** tab
→ You'll see "Build HandsFree Control APK" running
→ Wait for the green checkmark ✅

**5. Download the APK**
→ Click on the completed workflow run
→ Scroll to the bottom → **"Artifacts"** section
→ Click **"HandsFreeControl-debug-apk"** to download
→ You'll get a `.zip` file — extract it to get the `.apk` file

**6. Install on your phone** → See [Installing the APK](#installing-the-apk) below

---

## Method 2 — Build Script (Mac or Linux) 🖥️

If you have a Mac or Linux computer:

**1. Install Java 17**
→ Download from: https://adoptium.net
→ Choose: **Temurin 17**, your OS, your architecture

**2. Run the build script**

Open Terminal in the `HandsFreeControl` folder:

```bash
chmod +x build_apk.sh
./build_apk.sh
```

The script automatically:
- Downloads the Android SDK (~150MB)
- Downloads the MediaPipe AI model (~25MB)
- Downloads all app dependencies (~500MB, first time only)
- Builds the APK

When done, you'll find `HandsFreeControl-debug.apk` in the project folder.

---

## Method 3 — Windows Build Script 🪟

If you have a Windows computer:

**1. Install Java 17**
→ Download from: https://adoptium.net
→ Choose: **Temurin 17**, Windows, x64

**2. Double-click `build_apk.bat`**

Or run from Command Prompt:
```cmd
build_apk.bat
```

When done, you'll find `HandsFreeControl-debug.apk` in the project folder.

---

## Installing the APK on Your Phone 📱

Once you have the `.apk` file:

### Enable Unknown App Installations
Android blocks APKs from outside the Play Store by default.

**Android 8.0+:**
1. Go to **Settings → Apps** (or Application Manager)
2. Tap the three-dot menu → **"Special app access"**
3. Tap **"Install unknown apps"**
4. Find your file manager or browser
5. Toggle **"Allow from this source"** ON

**OR** — just try to open the APK and Android will prompt you to enable it automatically.

### Transfer the APK to Your Phone

**Option A — Google Drive / iCloud / Dropbox (easiest)**
1. Upload the `.apk` to Google Drive on your computer
2. Open Google Drive on your phone
3. Tap the APK file → Download
4. Tap the downloaded file to install

**Option B — USB Cable**
1. Connect phone to computer via USB
2. Select "File Transfer" mode on your phone
3. Copy the `.apk` to your phone's Downloads folder
4. Open a file manager on your phone
5. Tap the APK to install

**Option C — ADB (Developer method)**
```bash
adb install HandsFreeControl-debug.apk
```
(Requires USB Debugging enabled in Developer Options)

### Install the APK
1. Open the `.apk` file on your phone
2. Tap **"Install"**
3. Tap **"Done"** or **"Open"**

### Final Step — Enable Accessibility Service
1. Open **HandsFree Control** app
2. Tap the **"Enable"** button on the warning
3. In Accessibility settings, find **"HandsFree Control"**
4. Toggle it **ON** and confirm
5. Return to the app → You're ready! 🎉

---

## Quick Reference: Which Method?

| Situation | Best Method |
|-----------|-------------|
| No coding tools on your computer | Method 1 (GitHub Actions) |
| Mac or Linux computer | Method 2 (build_apk.sh) |
| Windows computer | Method 3 (build_apk.bat) |
| Already have Android Studio | Open project → Run button |
