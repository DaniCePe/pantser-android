# Pantser Web Changelog

Every publishing ships with its own dated, versioned entry.

## [2026-10-23] [v1.4.0]
- New Utilities setting: "Barcode labels (experimental)" (switch, default off). When on, label sheets render each size as a Code 128 barcode with the size printed underneath in plain text, on the same page, margins and grid as text labels. The size is encoded exactly as typed (spaces and letter case included), in classic and Advanced mode alike.
- A size can now exist twice in history: once as a text label and once as a barcode label. They are kept as separate records, the quick-print tray marks barcode entries and reprints each entry in its own kind, and the startup duplicate cleanup no longer merges the two kinds together.
- Service worker cache bumped to pantser-v8.

## [2026-10-22] [v1.3.2]
- Fixed: pressing back after swiping to Insights still closed the app sometimes, and with the Ask setting the exit confirmation could fail to appear at all, from any menu. Screen changes used to push browser history entries, while only some back paths pushed one back, and Android Chrome can outright skip history entries that were not pushed from a real tap or keypress (its history manipulation intervention), which is why behaviour varied with how each entry had been created. Screen changes no longer touch browser history. Instead the app keeps a sacrificial trap entry above its base entry, re-armed provisionally after every back press and, crucially, re-armed from the user's next tap or keypress, which Chrome cannot skip. Back can only close the app from the home screen, through the existing back button setting, whose Ask confirmation is part of this fix and verified working.
- Added: the app version now appears at the bottom of the Info screen (below the user manual pages), so it is easy to confirm that an update actually arrived on your device. (Folded into this release at the maintainer's request rather than shipped as its own update.)
- Service worker cache bumped to pantser-v7.

## [2026-10-22] [v1.3.1]
- Fixed: after swiping to Insights, the Android back button could exit the app instead of returning home. The main pager had two controllers (the generic pager helper and the view switcher) that could disagree about what was on screen; the view switcher is now the only one, and back handling also checks what is actually displayed. Tapping Insights and going back already worked; both paths now behave the same.
- Fixed: in the two-box input, a digit typed while the first box was already full (for example right after backspace jumped back to it) was silently swallowed by the two-character limit. It is now typed into the second box instead, moving focus there.
- Service worker cache bumped to pantser-v6.

## [2026-10-22] [v1.3.0]
- New Utilities setting: "Two-box size input" (classic mode only). Two boxes with an x between them, like 32 x 30, using the phone's own number pad keyboard. Typing 2 digits moves to the next box, backspace on an empty box goes back, and tapping a box clears it. Advanced multi-column mode is unchanged.
- New Appearance setting: "UI scale" stepper (minus / percent / plus, 5% steps, 50 to 200%), scaling the whole interface through the root font size.
- The home screen text now survives switching to Settings, Insights or Info and coming back, for the rest of the session. Pressing Create Label clears it.
- Size identity is now the raw text: spacing and letter case count, so "32 x32", "32 x 32" and "32 X 32" each get their own PDF instead of reusing the first one typed. Saved sizes from older versions keep working as their own entries.
- The Android back button now goes from Insights back to the home screen, like it already did from Settings and Info. The back button setting (Ask user / One press / Two press / Disable) still applies on the home screen only.
- Service worker cache bumped to pantser-v5.
- SPEC.md sections 1, 2, 6 and 7 document the new settings and the raw size identity (web-only changes; the APK is unchanged).

## [2026-10-17] [v1.2.0]
- New app logo: option C (accent pants with double size tag), picked from 14 code-built contenders.
- New icon set: 192px, 512px, and maskable 512px.
- Service worker cache bumped to pantser-v4.

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
