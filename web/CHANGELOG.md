# Pantser Web Changelog

Every publishing ships with its own dated, versioned entry.

## [2026-10-30] [v1.10.0]
- Added: item-label sheets now print as an HTML rendering on touch devices, the same instant system print dialog size labels got in v1.9.0, instead of opening a PDF tab. The sheet mirrors the PDF exactly (2x2 cells, 10% smaller logo with the v1.9.2 shift, Tinos rows with hanging indent, labels rotated into their cells). Desktop keeps the PDF path, and iOS installed web apps keep the PDF new-tab flow.
- Service worker cache bumped to pantser-v20.

## [2026-10-29] [v1.9.2]
- Changed: the item-label logo is 10% smaller and shifted up by the shrink amount, so the ITEM / DESCRIPTION / QTY block rides up about twice the shrink and no longer hugs the bottom of the label.
- Changed: item labels are now on by default (fresh installs get the Item labels page and home toggle without digging into Settings; existing installs keep whatever the switch was already set to).
- Service worker cache bumped to pantser-v19.

## [2026-10-29] [v1.9.1]
- Fixed: live address updated to https://gst-4649.github.io/pantser-android/web/ after the GitHub account rename. The v1.9.0 release notes pointed to the old danicepe.github.io address, which no longer serves the site.
- Service worker cache bumped to pantser-v18.

## [2026-10-29] [v1.9.0]
- Added: on touch devices, size-label sheets now print as an HTML rendering instead of a PDF, so tapping Print opens the system print dialog at once instead of opening a new tab. The sheet mirrors the PDF geometry exactly (0.15" standard margins, custom side margins on 4x15, same barcode math with bars as SVG). Desktop keeps the PDF path, which already works perfectly there. iOS installed web apps keep the PDF new-tab flow, since window.print() does nothing in their webview. Item-label sheets still use the PDF flow.
- Service worker cache bumped to pantser-v17.

## [2026-10-26] [v1.8.1]
- Changed: the standard side margin for size-label sheets is now 0.15" instead of 0.30", so columns sit more spread out by default. The old 0.30" is still reachable through the Advanced page's Side margins stepper (0.00" to 0.30").
- Changed: the Advanced settings page is no longer marked (experimental).
- Service worker cache bumped to pantser-v16.

## [2026-10-26] [v1.8.0]
- Added: Advanced settings page in the Settings pager, marked (experimental): a Custom margins switch plus a Side margins stepper. When on, 4 x 15 size-label sheets use your side margins instead of the standard 0.30", with left and right edges moving together for symmetry. The stepper moves in 0.05" steps from 0.00" (columns spread to the paper edges) to 0.30" (today's margins); some printers clip at the very edge, so 0.05" is the almost-touching end. Barcodes share the same geometry, so they spread too. Other layouts and the item-label sheets keep the standard margins. Saved margins survive toggling the switch off.
- Service worker cache bumped to pantser-v15.

## [2026-10-26] [v1.7.2]
- Changed: barcode sheets for all-digit sizes of even length now render as Code 128 set C (digit pairs) instead of set B — the same form as the photo barcode alex scanned, and the form bwip-js picks for such strings. It decodes to the same number; only the bar pattern is denser. Anything with letters, spaces, or an odd digit count stays set B exactly as before.
- Changed: reprinting a digit sheet that was first printed before this update regenerates it as set C under the same record, instead of reprinting its old set-B bytes.
- Service worker cache bumped to pantser-v14.

## [2026-10-26] [v1.7.1]
- Fixed: on desktop, the Create item labels button stretched to the left edge instead of sitting centered like the home Create button. It now gets the same centered pill treatment in the wide layout. Phone portrait layout is unchanged.
- Fixed: the Settings pages (and the Info pages, which share the same pager) could only be turned by a finger swipe, so mouse and keyboard users on desktop were stuck. They now also respond to the left and right arrow keys, and left/right arrows appear on the edges of those pages in the wide layout. Arrow keys never fire while typing in a field or working a control.
- Service worker cache bumped to pantser-v13.

## [2026-10-25] [v1.7.0]
- New Utilities setting: "Item labels" (switch, default off). When on, an Item labels page appears in the bottom bar between Create label and Insights, swipeable in dock order, with three boxes: ITEM #, DESCRIPTION and QTY.
- Item labels generate a US Letter sheet of 4 identical box labels, 3.5 by 5 inches each with a 0.5 inch gutter. Each label is drawn readable-first (logo on top, then ITEM / DESCRIPTION / QTY) and rotated 90 degrees counter-clockwise into its cell, so a long description wraps inside its own label with a hanging indent and never spills into another cell. The Samara Vendrell Products letterhead is bundled, and text uses the bundled open-licence Tinos font (labels bold 20pt, ITEM/QTY values 20pt, DESCRIPTION value 18pt).
- The Item labels page keeps its own history, separate from the size-label tray: tap an entry to refill the three boxes, Print reprints its sheet, Delete removes it. Item history is not counted in Insights totals and is not touched by the 7-day cleanup.
- When the switch is off, the app is unchanged: two bottom tabs, two pager pages.
- Service worker cache bumped to pantser-v12.

## [2026-10-23] [v1.6.0]
- Added: a wide layout for desktop windows and landscape phones. The top bar and bottom bar stretch to the edges of the screen with the title centered, page content sits in a readable centered column, and the home screen's size box becomes a long centered bar with the Create button under it. Phone portrait layout is unchanged.
- Added: in the wide layout, the Two-box size input and Barcode labels switches also appear as cards at the top left of the home screen. They mirror the switches in Settings — flipping one flips the other. Two-box input is on by default on wide screens until it is changed.
- Added: on desktop-sized screens the size box is focused automatically when the app opens, with the caret waiting in the left box — just start typing.
- Changed: Barcode labels is no longer marked experimental.
- Service worker cache bumped to pantser-v11.

## [2026-10-23] [v1.5.0]
- Added: a Check for updates button on the Info screen, under the version label. Tapping it forces the app to check for a newer version right away; when one is found, the button becomes Restart to update and one tap restarts the app into the new version. Saved labels and settings are not affected.
- Service worker cache bumped to pantser-v10.

## [2026-10-23] [v1.4.1]
- Adjusted: barcode labels are now taller and narrower, closer to a grocery-store barcode. The bars take up most of each cell's height and the code no longer stretches to fill the cell's width.
- Service worker cache bumped to pantser-v9.

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
