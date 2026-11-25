# Privacy Policy for AutoSpot

**Last updated:** November 2026

## Introduction

AutoSpot ("we", "our", or "us") is committed to protecting your privacy. This Privacy Policy explains how our mobile application collects, uses, and safeguards your information.

## Information We Collect

### Location Data
- **What:** Your precise GPS location (latitude and longitude)
- **When:** When you disconnect from your vehicle's Bluetooth, or when you manually save your location
- **Why:** To save and display your parking location so you can navigate back to your car
- **Street name lookup:** To show the street and town of each saved spot instead of raw coordinates, the app asks the device's geocoding service (Google on most phones) to translate the coordinates into an address. Only the coordinates of the spot being named are sent, and the result is stored with it. Without connectivity the spot keeps its coordinates and the name is filled in later.
- **Storage:** Stored locally on your device. If Android backup is enabled, it is also copied to your own Google account.

### Bluetooth Device Information
- **What:** Bluetooth device name and MAC address of your paired vehicles
- **When:** When you configure a vehicle in the app
- **Why:** To detect when you disconnect from your car and automatically save your location
- **Storage:** Stored locally on your device. If Android backup is enabled, it is also copied to your own Google account.

## How We Use Your Information

We use the collected information solely to:
1. Save your parking location when you disconnect from your car's Bluetooth
2. Display your saved parking locations on a map
3. Show the street and town of each saved location instead of its coordinates
4. Provide navigation to your parked car via Google Maps
5. Allow you to share your parking location with others

## Data Storage and Security

- **On-device storage:** Your vehicles, parking history and their street names are stored locally on your device
- **No Server Transmission:** We do not transmit your personal data to any external servers, and AutoSpot has no server that receives it. The only outbound request is the street-name lookup described above, which goes to the platform geocoding service and carries only the coordinates of the spot being named
- **Google Backup:** If you enable Android backup, your vehicles and parking history are included in the backup to your own Google account. The platform encrypts that backup; AutoSpot does not receive, collect, or have access to it. You can disable or delete the backup from your Google account settings. A direct device-to-device transfer also copies the database between your own devices.
- **Encryption:** Data stored on your device is protected by your device's security measures, and Android encrypts cloud backups

## Third-Party Services

### Google Maps
- We use Google Maps to display maps and provide navigation
- When you use the map or navigation features, your interactions are subject to [Google's Privacy Policy](https://policies.google.com/privacy)
- We do not share your parking history with Google; only the current location you choose to navigate to is sent to Google Maps

### Platform geocoding (reverse geocoding)
- To show the street and town of a saved spot, the app uses the geocoding service provided by your device's platform, which is Google Play services on most Android phones
- Each lookup sends only the latitude and longitude of the spot being named, and only once per spot; the returned address is stored in your local database
- That service is subject to [Google's Privacy Policy](https://policies.google.com/privacy). AutoSpot receives only the address, keeps it on your device, and sends nothing else

## Data Sharing

We do **not**:
- Sell your data to third parties
- Share your data with advertisers
- Transmit your data to our servers
- Use your data for analytics or tracking

The only time your location is shared externally is:
- When **you** choose to share it using the share button
- When **you** navigate to your car (location sent to Google Maps)
- When the app looks up the street name of a saved spot (coordinates sent to the platform geocoding service)

## Your Rights and Choices

### Data Access
- View all your saved locations directly in the app

### Data Deletion
- Delete individual parking locations from your history
- Clear all parking history for a vehicle
- Delete vehicles and all associated data
- Uninstall the app to remove all data

### Permissions
You can revoke app permissions at any time through your device settings:
- Location permission
- Bluetooth permission

Note: Revoking permissions will limit app functionality.

## Children's Privacy

AutoSpot is not directed at children under 13. We do not knowingly collect information from children under 13.

## Changes to This Policy

We may update this Privacy Policy from time to time. We will notify you of any changes by updating the "Last updated" date at the top of this policy.

## Contact Us

If you have questions about this Privacy Policy, please contact us at:

**Email:** me@dicren.com

---

## Summary

| Data Type | Collected | Stored | Shared | Purpose |
|-----------|-----------|--------|--------|---------|
| GPS Location | Yes | On your device; your own Android backup if enabled | Only when you choose, or with the platform geocoding service to name a saved spot | Save parking spot and show its street |
| Bluetooth info | Yes | On your device; your own Android backup if enabled | No | Detect car disconnect |
| Personal info | No | - | - | - |
| Usage analytics | No | - | - | - |

**AutoSpot respects your privacy. Your data stays in your account, not on our servers.**
