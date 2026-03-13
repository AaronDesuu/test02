MeterKenshin — Release Notes

Version 1.01.0 — March 13, 2026

New Features:

Add New Meter
- Users can now add new meters directly from the app
- Date picker support for Load Profile (LP), Event Data (ED), and Billing Data (BD)

App Tutorial
- Interactive step-by-step tutorial accessible from Settings → Help & Support
- Animated walkthrough using HorizontalPager with page indicators
- "Get Started" action navigates directly to the home screen

Help & Documentation
- In-app documentation viewer via WebView with responsive CSS styling
- Dark-themed HTML documentation embedded in app assets

Bug Fixes:

- Fixed: Reset Meter no longer leaves saved billing data behind — billing data is now properly cleared on reset
- Fixed: Average voltage values in Load Profile now display with correct decimal points
- Fixed: Updated meter specification field types for accuracy

Improvements:

- Enforced portrait-only orientation for consistent layout
- MeterCard now shows "Last Inspection" label instead of previous wording
- Receipt branding configuration moved from Settings to the Receipt screen for better discoverability
- Refined Receipt screen layout and presentation
- Upgraded Gradle and Android Gradle Plugin to latest versions
- Replaced deprecated API calls with modern Kotlin equivalents (`toColorInt()`, `edit {}`)

---

Version 1.00.0 — February 25, 2026

Initial Release

Features:

Meter Reading
- Discover and connect to utility meters via Bluetooth Low Energy (BLE)
- Automatic meter scanning with smart backoff to prevent connection issues
- Read meter data using DLMS/COSEM protocol (HDLC session: Open → Session → Challenge → Confirm)
- Real-time RSSI signal strength display per meter
- Meter list with filter and sort controls for quick navigation

Billing
- Automatic billing calculation from meter readings using configurable rate tables (`rate.csv`)
- Import and export billing data as CSV
- Per-meter billing history stored locally
- View saved billing records directly from the meter detail screen

Receipt Printing
- Wireless receipt printing via Woosim Bluetooth thermal printer
- Auto-connect to printer using `printer.csv` configuration
- Customizable receipt branding (company name, logo, contact details)
- Receipt preview before printing

Batch Printing
- Print receipts for multiple meters in one automated session
- Pre-flight preview showing how many meters will print or be skipped
- Smart error handling: Retry, Skip, or Cancel on printer errors
- Progress saved automatically — resumes after app crash or force-close
- Estimated time display before starting a batch

Data Management
- Import meter registry from CSV (`YYYYMM_meter.csv`)
- Export meter list to Downloads or share via any app (Admin only)
- Exported meters are reset to "Not Inspected" state for Reader import workflow

User Roles
- Admin: Full access including settings, export tools, and rate configuration
- Reader: Meter reading and receipt printing only
- Role-based UI — Admin-only features hidden from Reader accounts

App Experience
- Light and Dark theme support using Material You tokens
- In-app notification bar for scan, print, and connection status
- Navigation drawer for quick access to all screens
