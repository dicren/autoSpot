# AutoSpot

**Never forget where you parked your car again.**

AutoSpot is an open-source Android app that automatically saves your parking location when you disconnect from your car's Bluetooth. No manual action required - just park and go.

## Features

- **Automatic Location Saving** - Detects when you disconnect from your car's Bluetooth and saves your location automatically
- **Multiple Vehicles** - Support for multiple cars, each with its own parking history
- **Navigation** - One-tap navigation to your parked car via Google Maps
- **Parking History** - View all your previous parking locations
- **Share Location** - Share your parking spot with friends or family
- **Home Screen Shortcut** - Quick access to navigate to your car
- **Privacy First** - All data stays on your device. No accounts, no tracking, no ads
- **Multi-language** - Available in 9 languages: English, Spanish, French, German, Italian, Russian, Japanese, Chinese, and Korean

## How It Works

1. Pair your car's Bluetooth with your phone (if not already done)
2. Open AutoSpot and add your vehicle by selecting its Bluetooth device
3. That's it! Every time you park and turn off your car, your location will be saved automatically

The app runs a background service that monitors Bluetooth connections. When it detects a disconnection from a configured vehicle, it saves your current GPS location.

## Requirements

- Android 12 (API 31) or higher
- Google Play Services (for Google Maps)
- Bluetooth capability
- Location permissions (including background location)

## Permissions

| Permission | Why it's needed |
|------------|-----------------|
| **Location (Always)** | To save your location when Bluetooth disconnects, even when the app is closed |
| **Bluetooth** | To detect when you disconnect from your car |
| **Foreground Service** | To monitor Bluetooth in the background |
| **Receive Boot Completed** | To restart the monitoring service after device reboot |

## Building from Source

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11 or higher
- Google Maps API key

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/dicren/autoSpot.git
   cd autospot
   ```

2. Create a `local.properties` file in the project root (if not exists) and add your Google Maps API key:
   ```properties
   MAPS_API_KEY=your_google_maps_api_key_here
   ```

3. Open the project in Android Studio

4. Build and run:
   ```bash
   ./gradlew assembleDebug
   ```

### Getting a Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the "Maps SDK for Android" API
4. Create credentials (API key)
5. Restrict the API key to your app's package name and SHA-1 certificate

## Project Structure

```
app/src/main/java/com/dicren/autospot/
├── MainActivity.kt              # Main entry point
├── CreateShortcutActivity.kt    # Handles shortcut creation
├── NavigateActivity.kt          # Handles navigation shortcuts
├── data/
│   ├── AppDatabase.kt           # Room database
│   ├── LocationRepository.kt    # Data repository
│   ├── ParkingLocationDao.kt    # Parking location DAO
│   ├── ParkingLocationEntity.kt # Parking location entity
│   ├── VehicleDao.kt            # Vehicle DAO
│   └── VehicleEntity.kt         # Vehicle entity
├── service/
│   ├── BluetoothMonitorService.kt  # Background Bluetooth monitoring
│   └── LocationSaveService.kt      # Location saving service
├── receiver/
│   └── BootReceiver.kt          # Restart service on boot
└── ui/
    ├── OnboardingScreen.kt      # Onboarding flow
    ├── ParkingScreen.kt         # Main UI
    └── theme/                   # App theme
```

## Privacy

AutoSpot respects your privacy:

- **No data collection** - All data is stored locally on your device
- **No analytics** - We don't track how you use the app
- **No ads** - The app is completely ad-free
- **No accounts** - No sign-up required
- **Open source** - You can verify exactly what the app does

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for the full privacy policy.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Google Maps Android SDK](https://developers.google.com/maps/documentation/android-sdk)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)

## Support

If you encounter any issues or have suggestions, please [open an issue](https://github.com/dicren/autoSpot/issues).

---

Made with :heart: by [Dicren](https://github.com/dicren)
