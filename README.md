# DailyWidget 📱

A highly customizable, feature-rich Android widget application designed for productivity and utility. DailyWidget combines offline event tracking, live environment monitoring, and personal safety features into a single, battery-efficient home screen tool.

### ✨ Key Features
*   **Smart Event Management:** Offline-first daily highlights and upcoming event tracking with midnight auto-rollover.
*   **Live Environment Monitoring:** Real-time weather updates via OpenWeatherMap API and native device battery temperature tracking.
*   **Discreet Fake Call Engine:** A hidden "escape hatch" triggered by tapping the widget 5 times. Instantly simulates a highly realistic incoming call (with custom caller ID, profile picture, and voice audio) to help exit uncomfortable social situations.
*   **Silent Auto-Backups:** A zero-drain background scheduler that automatically zips and archives all user data, events, and settings locally on the last day of every month.
*   **Integrated UPI Support:** Native deep-linking to system UPI apps for seamless, zero-fee developer support.

### 🛠️ Tech Stack
*   **Language:** Kotlin / XML
*   **Architecture:** BroadcastReceivers, PendingIntents, AlarmManager (Background Scheduling)
*   **APIs:** OpenWeatherMap API, Android BatteryManager, Android MediaPlayer
*   **Data Persistence:** Encrypted SharedPreferences & JSON local storage with `.dwbak` ZIP archiving.

### 🚀 Installation
1. Download the latest `.apk` from the Releases tab.
2. Install the app on your Android device.
3. Long-press your home screen, navigate to Widgets, and drag DailyWidget onto your screen.
4. Tap the widget settings to customize your clock face, add events, and configure the Fake Call escape hatch.

*Developed by [Styrish Loy Rodrigues](https://github.com/StyrishRodrigues)*
