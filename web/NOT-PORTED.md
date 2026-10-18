# NOT-PORTED.md — Pantser web v1

Every APK behavior that could not move to the web 1:1, with the reason and
the alternative. Nothing here was cut silently.

## 1. Android system print dialog (PrintManager)

Why: the web has no print manager API. Printing is owned by the browser.
Alternative: after the PDF is generated, it opens in a hidden frame and the
browser print dialog is invoked. The user picks the printer there. The PDF
itself is locked to US Letter geometry, matching the NA_LETTER attribute.

## 2. Print job name and print attributes

Why: "Pant Size x \<size\>" job names and media attributes are Android
PrintManager concepts with no web equivalent.
Alternative: none. The downloaded file carries the same `size_<clean>.pdf`
name the APK uses.

## 3. Back-button behavior settings

Why: a web page cannot intercept the OS back button the way an activity
can, and a page cannot close its own tab (window.close only works for
script-opened windows).
Alternative: the setting is kept and honored through the History API.
In-app back from secondary views returns home. At the top level, Ask user
shows the confirm dialog, Two press exit arms on first press, One press
exit attempts to close, Disable exit stays. If the browser refuses to
close the tab, the user closes it by hand.

## 4. Keyboard inset auto-collapse (adjustResize)

Why: no web equivalent of the Android window-inset API that collapses the
header when the keyboard opens.
Alternative: responsive CSS. The layout scrolls and the focused input
stays visible through the browser's native scroll-into-view.

## 5. SharedPreferences and filesDir storage

Why: browsers cannot touch app-private files.
Alternative: settings live in localStorage under the same `label_store`
name and keys; PDF bytes live in IndexedDB. The 7-day cleanup rule,
duplicate consolidation, and record shape are unchanged.

## 6. "Prints are tracked only for successful document preparation"

Why: the browser cannot report whether the user completed or cancelled
the print dialog.
Alternative: none. A print counts when the PDF is generated and the
print dialog is launched. Documented here so the counter's meaning is
exact.

## 7. Export to external storage

Why: no shared external storage on the web.
Alternative: the chosen PDF downloads as a file through the browser.

## 8. Delete cache

Why: the web build keeps no temp files; the service worker cache is the
offline copy and must not be wiped by this action.
Alternative: none. The row is kept for settings parity and confirms with
a toast.

## 9. Non-default themes and palettes

Why: v1 scope decision, not a platform limit.
Alternative: deferred, not cut. The palette and style dialogs list all
options with CSS token slots reserved; only Linear Modern + Ember Night
(Crimson Dusk) is active in v1.

## 10. Typeface

Why: Android draws with DEFAULT_BOLD (Roboto Bold on device); the web
uses Helvetica Bold, the closest universally available match.
Alternative: none. The 24pt size, black color, centering, and the
-(ascent + descent) / 2 baseline formula are identical; Helvetica's own
ascent (718) and descent (-207) feed the formula.
