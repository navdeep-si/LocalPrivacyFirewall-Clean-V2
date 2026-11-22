# Local Privacy Firewall — Clean V2

**Kotlin Android project** — personal, user-controlled app network isolation. Compatible with Android 14, 15, 16 and 17, including Samsung Galaxy Z Fold.

## What it does

- Displays installed apps and lets the owner select which apps to isolate.
- After *explicit Android VPN approval*, starts a `VpnService` and routes selected apps' IPv4, IPv6 and DNS traffic into a local TUN interface.
- Reads and **discards** all received packets. It does not proxy, upload or forward packets and **does not request INTERNET permission**.
- Unselected apps retain internet access unless Android system VPN lockdown settings change the behavior.
- Includes search, app toggles, Start/Stop, locally stored selections, notification with Stop action and a link to system VPN settings.
- Can be configured as Android Always-on VPN in system Settings by the device owner.

**Limitations:** This is a network-only firewall. It does not suspend processes, interfere with crash handlers, clear local queued logs, prevent upload after disabling isolation, or guarantee that companies cannot observe connectivity failure. Some Android services/system UIDs may behave differently. The Android VPN indicator and notifications remain visible.

## Compatibility and build choices

- Runtime: `minSdk 34` (Android 14+); works on Android 14–17 using supported VPN APIs.
- SDK **used for compilation**: Android 16 API 36 (stable) to avoid the unavailable/mismatched `platforms;android-37` package that previously broke GitHub builds.
- `targetSdk 36`, Android Gradle Plugin 9.5.0, Gradle 9.6.0, JDK 17.
- Android Gradle Plugin 9.5 has **built-in Kotlin**. Do **not** add the legacy Kotlin Android plugin.
- No root, separate VPN server, Firebase, advertising SDK, analytics SDK or external libraries.
- Intended for private sideloading. Google Play severely restricts `QUERY_ALL_PACKAGES` usage.

## Upload to a fresh GitHub repository

1. Delete your old repository if desired; **don't upload old files into this project**.
2. Create a new empty GitHub repository (suggested name: `LocalPrivacyFirewall`). When creating it, **do not** pre-add a README, `.gitignore`, licence or starter workflow.
3. Extract this ZIP to your PC. Open the extracted **`LocalPrivacyFirewall_Clean_V2` directory**.
4. In the new repo choose **Add file → Upload files**, then drag **the files and folders inside that directory** (not the outer directory). Hidden folders starting with a dot such as `.github` may be hidden in your file manager — ensure `.github/workflows/build-apk.yml` is included. Prefer local Git command line if the browser omits `.github`.
5. Verify the GitHub Code tab shows `app/`, `build.gradle.kts`, `settings.gradle.kts`, `.github/` directly at the repository root.
6. In GitHub, open **Actions → Build Android Debug APK**. A push to `main` triggers the job automatically; otherwise choose **Run workflow**.
7. After a successful run, click the run and download **LocalPrivacyFirewall-debug-apk** under **Artifacts**. Extract it to obtain `app-debug.apk`. Sideload it onto your Samsung Fold.

**Important:** A green Actions check only proves an APK exists if the run contains the **Compile debug APK** and **Upload APK** steps and the run has an artifact. A blank template `CI` job is not the APK builder.

## Test on the phone

1. Open the app and choose **one harmless test app** with a recognizable internet-dependent screen (not your dialer, Settings, banking, or essential system apps).
2. Toggle it on, tap **START BLOCKING**, and approve Android's VPN consent dialog.
3. Confirm the system VPN indicator and firewall status notification are present.
4. Try an internet feature in the **selected** app: it should fail. Try an **unselected** app: it should normally still connect.
5. Reopen firewall, choose **STOP**, and verify the selected app reconnects. Some apps may need to retry their requests.
6. Test both Wi-Fi and cellular data; try switching networks while the firewall is running.
7. If you enable Android's **Block connections without VPN**, check behavior for every app because this global setting can also block unselected apps.

## Folders

```text
.github/workflows/build-apk.yml     GitHub APK compiler and artifact uploader
app/build.gradle.kts              Android app compile settings
app/src/main/AndroidManifest.xml    Permissions and VPN service declaration
app/src/main/java/com/localprivacy/firewall/MainActivity.kt
app/src/main/java/com/localprivacy/firewall/FirewallVpnService.kt
app/src/main/java/com/localprivacy/firewall/Rules.kt
app/src/main/res/                 App name and drawable icon
build.gradle.kts                  Android Gradle Plugin version
settings.gradle.kts               Maven and plugin repositories
gradle.properties                  Build options
```

## Security and privacy

The app has no internet permission and the VPN does not forward traffic. All selected package names and local statuses remain in private preferences. Android can revoke VPN consent, a user can stop the service, and only one VPN can be active at a time. Blocking traffic does not disable the target app's internal telemetry collection; it only blocks the network path available through the local VPN tunnel.

## Local Android Studio development

Open the extracted root folder as a project in an up-to-date Android Studio supporting Android Gradle Plugin 9.5. GitHub builds use the `gradle/actions/setup-gradle@v6` action to install Gradle, **so a binary Gradle wrapper is not included**. For local command line builds, install Gradle 9.6 and Android SDK 36, then run `gradle :app:assembleDebug`.

## Troubleshooting

- `No such platform android-37`: You uploaded old files. New workflow only requests `platforms;android-36`.
- `CI` with Hello World and no artifact: Remove the sample `.github/workflows/blank.yml`; keep just the supplied `build-apk.yml`.
- APK missing: open the run's **Compile debug APK** failure logs, not earlier green jobs.
- `QUERY_ALL_PACKAGES` policy warning: project is for private sideloading; for Play distribution redesign app discovery and justify package visibility.
