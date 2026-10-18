# Pantser Web Changelog

Every publishing ships with its own dated, versioned entry.

## [2026-10-17] [v1.1.1]
- The "Install app" row now always shows in Settings > Utilities when running in a browser tab. If the browser offers no install prompt, it shows manual steps instead of hiding.
- Fixed: the row previously only appeared after the browser fired its install signal, so it could be missing on first visits.

## [2026-10-17] [v1.1.0]
- Added an in-app "Install app" button (Settings > Utilities). It triggers the browser install prompt on Android and desktop; on iPhone it shows the Share > Add to Home Screen steps.
- The installed app always opens on the home screen, no matter which view was open at install time.
- Service worker cache bumped to pantser-v2.
- SPEC.md section 11 documents the install row (web-only, no APK counterpart).

## [2026-10-17] [v1.0.0]
- Initial 1:1 web port of the Pantser APK as an installable, offline-first PWA.
- PDF generation with pdf-lib using the exact APK geometry: US Letter 612x792pt, all grid layouts, bold 24pt centered cell text.
- Full English and Spanish string tables, copied exactly from the APK.
- Quick-print tray, insights, settings (utilities, storage, appearance), info manual, default Linear Modern + Ember Night theme.
- web/NOT-PORTED.md lists everything that could not port, with reasons and alternatives.
- Repo restructured: android/ holds the native track, web/ holds the PWA.
