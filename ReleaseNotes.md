MeterKenshin — Release Notes

Version 1.00.2 — March 18, 2026

New Features:

Get by Period
- Load Profile, Event Log, and Billing Data now can GET Period specific data
- Choose "Get All Data" or "Get by Period" with a date picker dialog
- Date picker constrained up to 12 months back because of the Meter's capability
- Files saved by period include the date range in the filename

Export Screen Filters & Sort
- Filter by file type (Load Profile, Event Log, Billing Data)
- Filter by retrieval mode (All Data, By Period)
- Sort by date ascending/descending
- Category group headers with file count badges
- Data period display for all files (extracted from CSV content)

Meter Reading Screen Redesign
- Modern filter/sort controls with dropdown menus
- Multi-select location filter with search
- Blue dot indicators on active filters

Bug Fixes:

- Fixed: CancellationException catch ordering in Event Log and Billing Data period functions
- Fixed: DLMS operations now properly track Job for cancellation support

Improvements:

- Redesigned Meter Reading screen
- Redesigned Export screen
- Redesigned DLMS Log card inside Meter Detail screen
- Redesigned header in Import Data screen
- Redesigned header in Export Data screen
- Receipt Template "Connect" button now navigates to Home and auto-connects the printer
- All 8 DLMS operations now are cancellable

---

Version 1.00.1 — March 13, 2026

New Features:

Add New Meter
- Users can now add new meters directly from the app

App Tutorial
- Interactive step-by-step tutorial accessible from Settings → Help & Support

Help & Documentation
- In-app documentation viewer via WebView
- Dark-themed HTML documentation embedded in app assets

Bug Fixes:

- Fixed: Reset Meter no longer leaves saved billing data behind — billing data is now properly cleared on reset
- Fixed: Average voltage values in Load Profile now display with correct decimal points
- Fixed: Updated meter specification field types for accuracy

Improvements:

- Added Landscape design to the app
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
