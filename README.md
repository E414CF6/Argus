# Argus

> **Exam Wireless Proctor** — Real-time BLE & Wi-Fi signal intelligence for fair, device-free examinations.

Argus is an Android application built with Kotlin and Jetpack Compose that helps exam proctors detect hidden wireless
earbuds, smartwatches, BLE beacons, and personal Wi-Fi hotspots. Instead of relying on static MAC addresses—which modern
devices randomize—Argus fingerprints invariant packet metadata, filters multipath signal noise with a 1D Kalman filter,
and tracks anomalies against a pre-exam room baseline.

## How It Works

Argus operates as a three-phase state machine (`ExamDetectorEngine`):

1. **Phase 1 · Baseline Collection** *(60s default, configurable)*  
   Scan the empty exam room before candidates enter to establish a baseline of ambient BLE and Wi-Fi signals from
   hallways and adjacent rooms.
2. **Phase 2 · Live Differential Guard**  
   Continuously evaluates 5-second sliding windows against the baseline to flag:
    - **New Arrivals**: Previously unseen devices appearing repeatedly within a 15-second observation window above the
      RSSI threshold.
    - **Sudden Approaches**: Baseline or known devices exhibiting a sharp signal surge (default `+15 dB`).
    - **Power-Off Events**: Previously flagged devices that suddenly vanish past the timeout window (`15s` default).
3. **Phase 3 · Proximity Radar Tracking**  
   Lock onto any flagged device to pinpoint its physical location using live RSSI history, a custom Compose radar
   visualizer, and distance-scaled audio/haptic feedback.

## Key Features

- **MAC-Randomization-Resistant Fingerprinting** (`FingerprintGenerator`)  
  Generates short SHA-256 fingerprints from invariant BLE advertising fields (device name, company ID, manufacturer
  header bytes, sorted 16-bit service UUIDs) and Wi-Fi beacon metadata (SSID, capabilities, frequency band, OUI).
- **1D Kalman Filtering & Collision Clustering** (`RssiKalmanFilter`, `CollisionClusterer`)  
  Smooths raw RSSI multipath fluctuations and disambiguates generic or hidden-SSID devices colliding on the same
  fingerprint.
- **0–100 Risk Scoring** (`RiskScorer`)  
  Prioritizes alerts using four weighted signals:
    - Audio / wearable / hotspot signature match (`+35`)
    - Alert reason: New Arrival (`+25`) or Sudden Approach (`+20`)
    - Normalized signal strength (`-75 dBm` to `-45 dBm` → `0–30`)
    - Observation frequency within the last 15 seconds (up to `+10`)
- **Session History & Analytics**  
  Save completed proctoring sessions locally (`ScanHistoryRepository`) and review historical trends and statistics
  (`HistoryScreen`, `StatisticsScreen`).
- **Customizable & Bilingual**  
  Supports Light, Dark, and System themes, English and Korean (`values` / `values-ko`) localization, and configurable
  detector thresholds (`SettingsModal`).

## Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose, Material 3, Material Icons Extended
- **Concurrency & State**: Kotlin Coroutines, `StateFlow`
- **Android SDK**: `minSdk 26`, `targetSdk 35`, `compileSdk 37` (Java 17)
- **Testing**: JUnit 4, Google Truth, `kotlinx-coroutines-test`

## Project Structure

```text
app/src/main/java/argus/
├── Argus.kt                      # Application entry point
├── data/
│   ├── feedback/                 # Audio & haptic radar feedback controller
│   ├── history/                  # JSON codec & local session history repository
│   ├── preferences/              # App settings & detector configuration storage
│   └── scanner/                  # Android BLE & Wi-Fi scanner wrappers
├── domain/
│   ├── detector/                 # ExamDetectorEngine & RiskScorer
│   ├── fingerprint/              # SHA-256 packet fingerprinting
│   ├── model/                    # Detection & scan history domain models
│   ├── signal/                   # 1D RSSI Kalman filter & collision clusterer
│   └── stats/                    # Session statistics calculator
└── ui/
    ├── components/               # RadarVisualizer & formatting utilities
    ├── theme/                    # Material 3 Argus theme
    └── *.kt                      # Main, PermissionGate, History, Statistics, Settings screens & ViewModel
```

## Getting Started

### Prerequisites

- Android Studio (with JDK 17+)
- An Android device running Android 8.0 (API 26) or higher with Bluetooth LE and Wi-Fi scanning support

> **Tip:** On Android 9+, Wi-Fi scan throttling limits background Wi-Fi scans. For 5-second live Wi-Fi monitoring during
> exams, disable **Wi-Fi scan throttling** in Android **Developer Options**.

### Build & Run Tests

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```
