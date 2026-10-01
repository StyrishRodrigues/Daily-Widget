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

<img width="1024" height="1536" alt="ChatGPT Image Sep 13, 2026, 06_30_34 PM" src="https://github.com/user-attachments/assets/7a3339cf-cc49-4736-a614-89ee9f26855f" />
<img width="1024" height="1536" alt="ChatGPT Image Sep 13, 2026, 06_35_57 PM" src="https://github.com/user-attachments/assets/f838e015-8314-4d06-91bf-ee3719da2c99" />
<img width="1024" height="1536" alt="ChatGPT Image Sep 13, 2026, 06_39_06 PM" src="https://github.com/user-attachments/assets/61870538-860c-4534-8e77-1e11e84f858a" />
<img width="1024" height="1536" alt="ChatGPT Image Sep 13, 2026, 06_45_12 PM" src="https://github.com/user-attachments/assets/0db39d48-d8c3-4ed3-ad90-9efec8c0d416" />

