# MeterKenshin — Release Notes

## Version 1.0.0 — February 25, 2026

**Initial Release**

---

### Meter Reading

- Discover and connect to utility meters via Bluetooth Low Energy (BLE)
- Automatic meter scanning with smart backoff to prevent connection issues
- Read meter data using DLMS/COSEM protocol (HDLC session: Open → Session → Challenge → Confirm)
- Real-time RSSI signal strength display per meter
- Meter list with filter and sort controls for quick navigation

### Billing

- Automatic billing calculation from meter readings using configurable rate tables (`rate.csv`)
- Import and export billing data as CSV
- Per-meter billing history stored locally
- View saved billing records directly from the meter detail screen

### Receipt Printing

- Wireless receipt printing via Woosim Bluetooth thermal printer
- Auto-connect to printer using `printer.csv` configuration
- Customizable receipt branding (company name, logo, contact details)
- Receipt preview before printing

### Batch Printing

- Print receipts for multiple meters in one automated session
- Pre-flight preview showing how many meters will print or be skipped
- Smart error handling: Retry, Skip, or Cancel on printer errors
- Progress saved automatically — resumes after app crash or force-close
- Estimated time display before starting a batch

### Data Management

- Import meter registry from CSV (`YYYYMM_meter.csv`)
- Export meter list to Downloads or share via any app (Admin only)
- Exported meters are reset to "Not Inspected" state for Reader import workflow

### User Roles

- **Admin**: Full access including settings, export tools, and rate configuration
- **Reader**: Meter reading and receipt printing only
- Role-based UI — Admin-only features hidden from Reader accounts

### App Experience

- Light and Dark theme support using Material You tokens
- In-app notification bar for scan, print, and connection status
- Navigation drawer for quick access to all screens
