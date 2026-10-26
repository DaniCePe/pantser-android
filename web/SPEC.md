# Pantser Web PWA: Frozen Behavior Spec (v1)

Source of truth for the web track of the Pantser repo. Extracted from the
Android source at `android/` on 2026-10-17. The APK and the web build must
never drift from this document. 1:1 behavior parity with the APK is the bar.
Verification: every flow below is run against the web build and the resulting
PDFs are compared side by side with APK output. Nothing is called done until the actual deliverable is approved ("I approve this").

Web scope for v1: the default theme only (modern style + Ember Night palette).
The other 4 styles and 6 palettes slot in later; CSS token slots are reserved
for them. Anything that cannot port is listed in `web/NOT-PORTED.md` with the
reason and the alternative, or marked cut.

Repo layout: `android/` holds the native track (package com.pantser.landc,
minSdk 29, no storage permissions needed). `web/` holds this PWA. The unused
`android/app/src/main/res/layout/activity_main.xml` is stale; the APK builds
its UI programmatically and so does the web port.

## 1. Navigation model

- Bottom dock with two tabs: Create (home) and Insights. Tapping the active
  tab returns home.
- Settings gear button, top left. Info button, top right. Tapping either from
  its own screen returns home.
- Home and Insights are a two-page horizontal pager. Settings and Info are
  full-screen replacements, each a 3-page pager with dots indicator.
- Back button (homescreen): configurable in Settings. Options: Ask user
  (default; confirm dialog), One press exit, Two press exit (second press
  within 2 seconds), Disable exit. From secondary views (Insights, Info,
  Settings), back returns to home.
- Web v1.6.0: a wide layout activates on wide viewports (desktop
  windows, landscape phones) via a CSS media query. The app column
  constraint is lifted so the top bar and dock stretch edge to edge
  (title centered, settings left, info right), page content is capped
  in a centered column, and the home prompt, size input (a long
  centered pill) and Create button are centered. The portrait phone
  layout is byte-identical to before; Android is unaffected. In the
  wide layout the home screen also carries the two-box and barcode
  switches as cards at the top left, built by the same switch-row code
  and bound to the same store keys as the Settings rows, so both
  places always show the same state. While two_box has never been
  set, it resolves to on in the wide layout and off otherwise; an
  explicit user choice always wins. On desktop-sized viewports
  (min-width 900px and min-height 501px) the first size input is
  focused at startup, search-page style; a one-shot flag re-applies
  the focus across startup re-renders and is disarmed by the user's
  first key press or tap.
- Web v1.5.0: the Info screen shows the running version under its pager
  dots, with a Check for updates button below it. The button forces an
  immediate update check (fresh version lookup plus a service-worker
  update); when a newer version is found and installed, the button becomes
  Restart to update and reloads the app into the new version. Labels and
  settings live in storage, not in the app shell, and survive the restart.
- Web mapping: the native back-button behavior is handled by the shell, so
  this becomes in-app confirm dialogs and history handling; recorded in
  NOT-PORTED.md. Web v1.3.0: back from Insights returns home in the web
  build too (it previously fell through to the homescreen exit behavior);
  the configurable exit behavior applies on the home screen only.
  Web v1.3.1: the main pager has a single controller (the view switcher);
  the generic pager helper is only for the Settings and Info pagers, and
  back handling verifies what is actually on screen before deciding.
  Web v1.3.2: view changes never push browser history entries. The app
  keeps one sacrificial trap entry above its base entry, re-armed
  provisionally after every popstate and re-armed for real on the user's
  next pointerdown or keydown (entries pushed without a user gesture can
  be skipped by Chrome's history manipulation intervention; gesture-
  pushed ones cannot). Only an intentional exit leaves it disarmed, so
  the history stack cannot drain and back cannot slip out of the app.

## 2. Create flow

### 2.1 Classic mode (default)

- One text input, hint `e.g. 32 x 30`, single line.
- Header line above the input: `Using %1$s US Layout` with the current
  layout string (default `4x15`).
- Privacy note under the header: "Your label PDF is stored privately and
  reused by dimensions."
- "CREATE LABEL  ->" button below the input.
- Pressing it calls createLabel with the trimmed input text. Empty input
  shows the toast "Type in a size first."

Web v1.3.0, classic mode only: the "Two-box size input" Utilities setting
replaces the single input with two boxes and an `x` between them, for
sizes in the `zz x yy` shape. The boxes are plain text inputs with
`inputmode="numeric"`, so the phone's own number pad opens (never
`type="number"`, so leading zeros like `08` and a literal `00` survive;
there is no ODD substitution). Typing a second digit moves focus to the
next box; backspace on an empty box moves focus to the previous box;
tapping a box clears just that box. A digit typed while the first box is
already full (v1.3.1, e.g. right after backspace jumped back to it) is
forwarded into the second box and focus moves there; there is no
maxlength attribute, the two-digit cap is enforced while forwarding.
Create joins the boxes as
`<a> x <b>`. Enter in either box is Create Label. Non-numeric sizes are
entered by turning the setting off. Advanced mode is unaffected.

Web v1.3.0, all home inputs: typed text is a session draft
(sessionStorage). It survives switching to Settings, Insights or Info and
returning, and survives view rebuilds; a fresh app launch starts blank.
Pressing CREATE LABEL clears the active mode's draft immediately, before
any printing happens; the created size remains available in the
quick-print tray and history.

### 2.2 Advanced multi-column mode (Settings toggle)

- Enabled by the Print Mode setting. The single input hides and a row of N
  inputs appears, where N = the columns of the current layout (2, 3, or 4).
- Each input hint is `Column %1$d` with its number.
- Skipped-size asterisk: if a column has text while an earlier column is
  blank, the earlier column's hint gains a trailing ` *`.
- "Jump to previous blank size" button appears when any such skipped column
  exists; tapping it focuses the first blank column that precedes a filled
  one. The button hides when there is no skipped column.
- Backspace on an empty column moves focus to the previous column (cursor at
  its end).
- Typed text is kept across view rebuilds and tab switches (session
  draft; see 2.1). Pressing CREATE LABEL clears it, valid input or not.
- Pressing CREATE LABEL: if any column is blank, toast "Check all column
  sizes." and abort. Otherwise the sizes are joined with `|` (no spaces).

### 2.3 createLabel(size)

1. Trim the input. If empty, toast the error and return.
2. Look for an existing record whose size matches exactly (raw identity,
   see below). If found and its PDF file exists with length > 0, print it
   and return home.
3. Otherwise create a new record: id = current time in milliseconds,
   fileName = fileNameForSize(size), createdAt = id. Generate the PDF into
   the labels folder, save the record at the head of the list, record the
   size as unique, then print and return home.
4. Failures show the toast "Could not create label: <message>".

Size identity (APK and web v1.0 to v1.2.0): lowercase the string, then
remove all whitespace from each `|` segment; two records with the same
normalized size are duplicates.

Web v1.3.0 (web-only deviation): size identity is the RAW string after
trimming the ends. Case and internal spacing count, so `32 x32`,
`32 x 32` and `32 X 32` are three different sizes with three different
PDFs. Records are looked up, consolidated on startup, and counted as
unique by exact raw match only. Records saved by older versions keep
their stored raw strings and simply act as their own entries.

Web v1.4.0: record identity is raw size PLUS kind (`text` or `barcode`,
see the Barcode labels setting in section 6). Lookup and startup
consolidation match on both, so the text and barcode versions of one
size coexist as separate records with separate PDFs. Records saved by
older versions have no `kind` field and count as `text`.

PDF file names: `size_<clean>.pdf` where clean keeps the raw size's case,
maps each space to `_` and each `|` to `_-_`. If clean is longer than 50 characters, the name is
`size_adv_<hash>.pdf` using the string hash; characters outside the safe
set also add a `_<hash>` suffix so two different raw sizes never share a
file name.

### 2.4 Quick-print tray

- Lists records with printCount > 0.
- Sorting: "Last Used" (default) sorts by lastPrintedAt descending;
  "Frequently Used" sorts by printCount descending. Toggle in Settings.
- Deduplicate by exact raw size (web v1.3.0; previously by normalized
  size), keep the first 8.
- Each row shows the size (pipes rendered as ` | `) and `<n> prints`.
- Tapping a row calls createLabel with the raw stored size.
- Empty state text: "Your recently printed labels will appear here."

## 3. PDF generation (exact geometry)

The web PDF must match this drawing model exactly.

- Page: US Letter, 612 x 792 points (8.5 x 11 in at 72 DPI). One page.
- Margins: left 0.3 in (21.6 pt), right 0.3 in (21.6 pt), top 0.55 in
  (39.6 pt), bottom 0.47 in (33.84 pt).
- Grid: cols x rows from the layout setting. Default `4x15` (4 columns,
  15 rows). Options: `4x15`, `3x10`, `4x20`, `2x10`. Cell width =
  usable width / cols; cell height = usable height / rows.
- Text: bold sans-serif, 24 pt, black, centered horizontally in each cell.
  Vertical centering uses baseline offset -(ascent + descent) / 2.
- Cell content: for column c, use sizeTexts[c]; if there are fewer sizes
  than columns, reuse sizeTexts[0]; if none, the cell stays blank. So a
  classic single size fills every cell with that size; an advanced set of
  sizes fills one size per column down the rows.
- Print job name on Android: "Pant Size x <size>"; print attributes lock
  the media to US Letter (NA_LETTER).
- On Android, the print manager opens the system print dialog. On the web,
  the generated PDF feeds the browser print dialog; recorded in
  NOT-PORTED.md with the exact mapping.

## 4. Insights

- "Insights" title; subtitle: "A quick view of your activity. Prints are
  tracked only for successful document preparation."
- Three stat cards:
  - TOTAL LABELS EVER PRINTED / Cumulative successful print jobs / value =
    total_printed counter. Only successful print jobs increment it.
  - UNIQUE PDFS / Distinct dimensions generated / value = unique_sizes count.
  - SAVED PDFS / Current files in storage / value = number of records.
- "Most printed sizes": group records by the raw size string, sum print
  counts, take the top 5, render each as
  `%1$d.  %2$s     %3$d prints` (pipes rendered as ` | `).
- Storage line: `Storage used: %1$s`, formatted as B, KB, or MB (integer
  division). The total is the sum of the label PDF files.
- Empty history text: "No print history yet. Create a label to get started."

## 5. Info (user manual)

Three pages:

1. "Pantser User Manual". Body: "Welcome to Pantser, the premium,
   zero-maintenance label generator designed specifically for apparel size
   printing on US Letter pages."
2. "Core Concept: Dimension Identity & Reuse". Body: "Pantser eliminates
   file clutter entirely. When you generate a print dimension like '32 x 30',
   the app treats those dimensions as a unique identity. Instead of creating
   a new file every time, it intelligently checks for an existing file
   matching that exact size configuration, instantly reusing it."
3. "Step-by-Step Instructions". Body: "1. Enter Dimensions: Click the input
   field and type your size (e.g., '34 x 32' or 'M').\n\n2. Click Create:
   Press 'CREATE LABEL'. The app builds a high-resolution grid PDF or opens
   the existing one immediately.\n\n3. System Print: The Android system print
   dialogue will open automatically.\n\n4. Quick print: Once printed, it
   appears in your 'Quick print' tray for one-tap generation."
4. "Settings Guide". Body: "- Theme Mode: Switch between System Default,
   Dark Mode, and Light Mode.\n\n- Theme Palette: Choose from custom blended
   themes like Ember Night, Lavender Cream, or Spring Pastel.\n\n- Paper
   Layout: Configure the grid columns and rows.\n\n- Export & Cleanup:
   Extract PDFs to downloads or manage storage via cache/old file removal."

Note: on Android the manual is three pages (welcome+concept, steps,
settings). The web keeps the same grouping.

## 6. Settings

### 6.1 Utilities page

- Quick print sorting format: toggles between "Last Used" and "Frequently
  Used".
- Back button on homescreen: dialog with Ask user / One press exit /
  Two press exit / Disable exit (default Ask user).
- App Language: System default / English / Espanol (default System).
- Print Mode: Classic ("One Label Type") or Advanced ("%1$d Labels, %2$d
  Columns" with the layout's column count twice). Toggling rebuilds the
  home view.
- Two-box size input (web v1.3.0, switch, default off): classic mode home
  input becomes two boxes with an `x`; see 2.1. No effect in Advanced
  mode.
- Barcode labels (web v1.4.0, switch, default off; marked experimental until v1.6.0): when
  on, generated sheets render each size as a Code 128 barcode (set B,
  the raw size text encoded exactly, spaces and case included) with the
  size printed underneath in plain text, instead of the large bold size
  text. Applies to classic and Advanced mode. A size can exist as both
  a text record and a barcode record (record field `kind`); duplicates
  consolidate per size AND kind, and quick-print tray rows reprint with
  the record's own kind. Text that Code 128 set B cannot encode falls
  back to the normal text cell.
- Barcode set selection (web v1.7.2): an all-digit size of even
  length (2 or more digits) encodes as Code 128 set C (digit pairs),
  the form in alex's photo barcode; anything else stays set B. Both
  decode to the same text. A barcode record printed before v1.7.2
  still holds set-B bytes, so reprinting a digit sheet regenerates
  it as set C in place under the same record instead of reusing the
  old bytes. Multi-column sheets pick the set per cell at generation
  time; reprints of multi-column sheets reuse their stored bytes.
- Paper layout: dialog with 4 x 15, 3 x 10, 4 x 20, 2 x 10 (default 4 x 15).
- Export a saved PDF: lists records as "<size> x <date>", copies the chosen
  PDF to user-picked external storage. On the web this becomes a file
  download; recorded in NOT-PORTED.md.

### 6.2 Storage & Data page

- Automatic Cleanup (switch, default on): removes PDFs whose
  lastPrintedAt (or createdAt when never printed) is older than 7 days.
  Turning the switch on runs the cleanup immediately.
- Delete cache: clears temporary files.
- The bottom of the Info screen shows the running app version
  (from a single APP_VERSION constant in the web app).
- Delete old PDFs: dialog with Older than 7 days / Older than 30 days /
  Older than 90 days / Delete all saved PDFs. Uses createdAt. "Delete all"
  removes every record and every saved PDF file. Shows the toast
  "Deleted %1$d PDF%2$s."
- Reset statistics: confirm dialog ("Reset?" / "This clears all insights
  and print counts."), then zeroes total_printed, unique_sizes, and every
  record's printCount/lastPrintedAt.

### 6.3 Appearance page

- Theme Mode: System default / Light / Dark (default System).
- Theme Palette: 7 palettes (default Ember Night / Crimson Dusk).
- Theme Style: Linear Modern (default) / Retro 90s Nostalgia / Bauhaus
  Geometric / Terminal CLI / Dopamine Maximalism. Web v1 implements the
  default style only; slots are reserved for the rest.
- UI scale (web v1.3.0): minus / percent / plus stepper, 5% steps, 50 to
  200%, default 100%. Implemented as the root font size times the scale;
  the whole stylesheet is in rem so the layout reflows. CSS zoom is not
  used. The PDF geometry is not affected.

## 7. Persistence contract

SharedPreferences name on Android: `label_store`. Web equivalent:
localStorage (IndexedDB only if tray data outgrows it).

Keys and defaults:

| Key | Default | Meaning |
|---|---|---|
| records | [] | JSON array of {id, size, file, created, prints, lastPrinted} |
| total_printed | 0 | cumulative successful print jobs |
| unique_sizes | [] | JSON array of distinct generated sizes |
| automatic_cleanup | "true" | 7-day cleanup enabled |
| lang | "system" | system / en / es |
| mode | "system" | system / light / dark |
| palette | "EMBER_NIGHT" | active palette enum name |
| back_behavior | "ask" | ask / one_press / two_press / disable |
| ui_style | "modern" | modern / retro / bauhaus / terminal / dopamine |
| layout | "4x15" | 4x15 / 3x10 / 4x20 / 2x10 |
| quick_print_sort | "last" | last / freq |
| advanced_mode | "false" | classic / advanced |
| two_box | "false" | web v1.3.0: classic-mode two-box input on/off |
| barcode | "false" | web v1.4.0: barcode label sheets on/off |
| ui_scale | "100" | web v1.3.0: UI scale percent, 50 to 200 |

PDF storage: Android keeps them in filesDir/labels/. The 7-day cleanup
rule: a record is removed (file deleted, record dropped) when
lastPrintedAt (or createdAt when 0) is older than 7 days. Duplicates are
consolidated at startup as described in 2.3.

## 8. Default theme tokens (web v1)

Web v1 ships the default look only: Linear Modern style + Ember Night
palette. Tokenize it in CSS so the other 4 styles and 6 palettes can slot
in later. The styles and palette token tables for later are listed under
"reserved" below.

Default tokens:

- --bg: #FCF5F2 (light), #060404 (dark)
- --surf: #F5E4DC (light), #160D0A (dark)
- --acc: #D84315 (light), #FF7043 (dark)
- --text: #1C1625 (light), #FFFFFF (dark)
- --muted: #63535C (light), #8A8F98 (dark)

Dark mode: from the mode setting; "system" follows the OS. Headings and
primary buttons use --acc. Cards and rows use --surf with 10dp corner
radius. Default font: system sans; the PDF keeps bold sans-serif 24pt.

Reserved palettes (names and light/dark bg, surf, accent):
- ROYAL_GRAPE "Midnight Purple": #F4F4FC #E4E4F4 #5E6AD2 / #05050A #0E0E1C
  #5E6AD2
- SPRING_PASTEL "Garden Meadow": #F4FBF4 #E2EFE2 #2E7D32 / #040804 #0C140C
  #81C784
- LAVENDER_CREAM "Petal Soft": #FAF6FE #EDE2F7 #7B1FA2 / #080509 #150E1A
  #BA68C8
- EMBER_NIGHT "Crimson Dusk": see default tokens above
- BAUHAUS "Bold Primary": #F0F0F0 #FFFFFF #D02020 / #121212 #1E1E1E #D02020
- TERMINAL "Hacker Green": #0A0A0A #0A0A0A #33FF00 / #000000 #050505 #33FF00
- DOPAMINE "Neon Pop": #0D0D1A #1C1C30 #FF3AF2 / #05050A #0D0D1A #00F5D4

Reserved styles: modern (default), retro, bauhaus, terminal, dopamine.
Style-specific details (mascots, retro bevels, monochrome fonts) are not
part of v1.

## 9. String tables (frozen, EN + ES)

Placeholders keep Android positions: %1$s string, %1$d integer, %2$s
second string. The web dictionary must ship both tables in full.

### English

```
app_name = Pantser
retro_title = Pantser - [Main Window]
prompt_what_size = What size?
generate_pdf = Generate PDF
using_layout = Using %1$s US Layout
privacy_note = Your label PDF is stored privately and reused by dimensions.
hint_example = e.g. 32 x 30
create_label_btn = CREATE LABEL  →
label_column_x = Column %1$d
jump_to_previous_unsaved = <- Jump to previous blank size
err_invalid_column = Check all column sizes.
quick_print_title = Quick print
no_recent_labels = Your recently printed labels will appear here.
print_count_text = %1$d prints
err_empty_size = Type in a size first.
err_create_fail = Could not create label: %1$s
insights_title = Insights
insights_sub = A quick view of your activity. Prints are tracked only for successful document preparation.
stat_total_printed = TOTAL LABELS EVER PRINTED
stat_total_printed_desc = Cumulative successful print jobs
stat_unique_pdfs = UNIQUE PDFS
stat_unique_pdfs_desc = Distinct dimensions generated
stat_saved_pdfs = SAVED PDFS
stat_saved_pdfs_desc = Current files in storage
most_printed_title = Most printed sizes
no_history = No print history yet. Create a label to get started.
history_item_format = %1$d.  %2$s     %3$d prints
storage_used = Storage used: %1$s
manual_title = Pantser User Manual
manual_welcome = Welcome to Pantser, the premium, zero-maintenance label generator designed specifically for apparel size printing on US Letter pages.
manual_concept_title = Core Concept: Dimension Identity & Reuse
manual_concept_body = Pantser eliminates file clutter entirely. When you generate a print dimension like '32 x 30', the app treats those dimensions as a unique identity. Instead of creating a new file every time, it intelligently checks for an existing file matching that exact size configuration, instantly reusing it.
manual_steps_title = Step-by-Step Instructions
manual_steps_body = 1. Enter Dimensions: Click the input field and type your size (e.g., '34 x 32' or 'M').\n\n2. Click Create: Press 'CREATE LABEL'. The app builds a high-resolution grid PDF or opens the existing one immediately.\n\n3. System Print: The Android system print dialogue will open automatically.\n\n4. Quick print: Once printed, it appears in your 'Quick print' tray for one-tap generation.
manual_settings_title = Settings Guide
manual_settings_body = - Theme Mode: Switch between System Default, Dark Mode, and Light Mode.\n\n- Theme Palette: Choose from custom blended themes like Ember Night, Lavender Cream, or Spring Pastel.\n\n- Paper Layout: Configure the grid columns and rows.\n\n- Export & Cleanup: Extract PDFs to downloads or manage storage via cache/old file removal.
section_utilities = Utilities
setting_sort_format = Quick print sorting format
sort_last_used = Last Used
sort_freq_used = Frequently Used
setting_back_behavior = Back button on homescreen
back_behavior_ask = Ask user
back_behavior_one_press = One press exit
back_behavior_two_press = Two press exit
back_behavior_disable = Disable exit
dialog_exit_title = Exit app?
dialog_exit_msg = Are you sure you want to exit?
btn_exit = Exit
toast_press_again_exit = Press back again to exit
setting_layout = Paper layout
setting_export = Export a saved PDF
setting_export_sub = Copy to external storage
setting_language = App Language
section_storage = Storage & Data
setting_cleanup = Automatic Cleanup
setting_cleanup_sub = Automatically remove generated PDFs older than 7 days.
setting_delete_cache = Delete cache
setting_delete_cache_sub = Clear temporary files
setting_delete_old = Delete old PDFs
setting_delete_old_sub = Remove older files manually
setting_reset = Reset statistics
setting_reset_sub = Clear lifetime history
section_appearance = Appearance
setting_theme_mode = Theme Mode
setting_theme_palette = Theme Palette
setting_theme_style = Theme Style
dialog_reset_title = Reset?
dialog_reset_msg = This clears all insights and print counts.
btn_reset = Reset
btn_cancel = Cancel
mode_system = System default
mode_light = Light
mode_dark = Dark
style_modern = Linear Modern
style_retro = Retro 90s Nostalgia
style_bauhaus = Bauhaus Geometric
style_terminal = Terminal CLI
style_dopamine = Dopamine Maximalism
delete_7_days = Older than 7 days
delete_30_days = Older than 30 days
delete_90_days = Older than 90 days
delete_all = Delete all saved PDFs
toast_deleted = Deleted %1$d PDF%2$s.
toast_no_pdfs = No saved PDFs yet.
toast_exported = PDF exported.
toast_export_failed = Export failed: %1$s
toast_cache_cleared = Cache cleared.
toast_stats_reset = Stats reset.
lang_en = English
lang_es = Espanol
```

### Espanol (exact source text)

```
app_name = Pantser
retro_title = Pantser - [Ventana Principal]
prompt_what_size = ¿Qué tamaño?
generate_pdf = Generar PDF
using_layout = Usando diseño US %1$s
privacy_note = Su PDF se guarda de forma privada y se reutiliza por dimensiones.
hint_example = ej. 32 x 30
create_label_btn = CREAR ETIQUETA  →
setting_print_mode = Modo de Impresión
print_mode_classic = Clásico
print_mode_classic_sub = Un Solo Tipo de Talla
print_mode_advanced = Avanzado
print_mode_advanced_sub = %1$d Tallas, %2$d Columnas
label_column_x = Columna %1$d
jump_to_previous_unsaved = ← Ir al tamaño anterior vacío
err_invalid_column = Revise todos los tamaños.
quick_print_title = Impresión rápida
no_recent_labels = Tus etiquetas recientes aparecerán aquí.
print_count_text = %1$d impresiones
err_empty_size = Ingrese un tamaño primero.
err_create_fail = No se pudo crear la etiqueta: %1$s
insights_title = Estadísticas
insights_sub = Vista rápida de actividad. Solo se cuentan impresiones exitosas.
stat_total_printed = TOTAL ETIQUETAS IMPRESAS
stat_total_printed_desc = Trabajos de impresión exitosos acumulados
stat_unique_pdfs = PDFS ÚNICOS
stat_unique_pdfs_desc = Dimensiones distintas generadas
stat_saved_pdfs = PDFS GUARDADOS
stat_saved_pdfs_desc = Archivos actuales en el almacenamiento
most_printed_title = Tamaños más impresos
no_history = Sin historial aún. Crea una etiqueta para comenzar.
history_item_format = %1$d.  %2$s     %3$d impresiones
storage_used = Espacio usado: %1$s
manual_title = Manual de Usuario Pantser
manual_welcome = Bienvenido a Pantser, el generador de etiquetas premium diseñado para la impresión de tallas en hojas US Letter.
manual_concept_title = Concepto: Identidad y Reutilización
manual_concept_body = Pantser elimina el desorden de archivos. Cuando genera una dimensión como \'32 x 30\', la app la trata como una identidad única y reutiliza el archivo existente.
manual_steps_title = Instrucciones Paso a Paso
manual_steps_body = 1. Ingrese dimensiones: Escriba su talla (ej. \'34 x 32\' o \'M\').\n\n2. Clic en Crear: Pulse \'CREAR ETIQUETA\'. La app genera el PDF o abre el existente.\n\n3. Impresión: El diálogo de impresión del sistema se abrirá automáticamente.\n\n4. Impresión rápida: Aparecerá en su bandeja para generarlo con un toque.
manual_settings_title = Guía de Ajustes
manual_settings_body = • Modo: Cambie entre Predeterminado, Oscuro y Claro.\n\n• Paleta: Elija temas personalizados como Ember Night o Spring Pastel.\n\n• Diseño: Configure las filas y columnas.\n\n• Exportar: Guarde PDFs en descargas o limpie el almacenamiento.
section_utilities = Utilidades
setting_sort_format = Formato de orden de impresión
sort_last_used = Último uso
sort_freq_used = Más frecuente
setting_back_behavior = Botón de retroceso en inicio
back_behavior_ask = Preguntar al usuario
back_behavior_one_press = Salir con una pulsación
back_behavior_two_press = Salir con dos pulsaciones
back_behavior_disable = Deshabilitar salida
dialog_exit_title = ¿Salir de la aplicación?
dialog_exit_msg = ¿Estás seguro de que deseas salir?
btn_exit = Salir
toast_press_again_exit = Presiona atrás de nuevo para salir
setting_layout = Diseño de papel
setting_export = Exportar PDF guardado
setting_export_sub = Copiar al almacenamiento externo
setting_language = Idioma de la aplicación
section_storage = Almacenamiento y Datos
setting_cleanup = Limpieza Automática
setting_cleanup_sub = Eliminar PDFs con más de 7 días de antigüedad.
setting_delete_cache = Borrar caché
setting_delete_cache_sub = Limpiar archivos temporales
setting_delete_old = Borrar PDFs antiguos
setting_delete_old_sub = Eliminar archivos manualmente
setting_reset = Reiniciar estadísticas
setting_reset_sub = Borrar historial de por vida
section_appearance = Apariencia
setting_theme_mode = Modo del Tema
setting_theme_palette = Paleta del Tema
setting_theme_style = Estilo del Tema
dialog_reset_title = ¿Reiniciar?
dialog_reset_msg = Esto borrará todas las estadísticas y conteos.
btn_reset = Reiniciar
btn_cancel = Cancelar
mode_system = Predeterminado
mode_light = Claro
mode_dark = Oscuro
style_modern = Moderno Lineal
style_retro = Nostalgia Retro 90s
style_bauhaus = Geométrico Bauhaus
style_terminal = Terminal CLI
style_dopamine = Dopamina Maximalista
delete_7_days = Más de 7 días
delete_30_days = Más de 30 días
delete_90_days = Más de 90 días
delete_all = Borrar todos los PDFs
toast_deleted = %1$d PDF%2$s eliminados.
toast_no_pdfs = Sin PDFs guardados.
toast_exported = PDF exportado.
toast_export_failed = Fallo al exportar: %1$s
toast_cache_cleared = Caché limpiada.
toast_stats_reset = Estadísticas reiniciadas.
lang_en = Inglés
lang_es = Español
```

Note: the Espanol table above is the plain-ASCII rendering of the APK's
values-es strings. The web dictionary must use the exact accented source
text from `android/app/src/main/res/values-es/strings.xml` as the
authoritative copy; accents were flattened here only for this rendered
view, not as a change to the strings.

## 10. Open questions

None blocking v1. Hosting is GitHub Pages. PDF generation uses pdf-lib.
Hosting fallback is Home Cloud. alex and Nova get the web track after
v1 approval; no third-party changes before that.

## 11. Web-only: install row (no APK counterpart)

Settings > Utilities shows an "Install app" row whenever the app runs
in a browser tab; the row hides itself once installed. Tapping it triggers
the browser install prompt when one is available; on iPhone it shows the
Share > Add to Home Screen steps; otherwise it shows generic browser-menu
steps. The installed PWA always launches at the manifest start_url (the
app root), so it always opens on the home screen regardless of which view
was open at install time. Strings for this row live in js/strings-web.js,
merged over the frozen APK tables.

## 12. Item labels (v1.7.0)

- Utilities switch `item_labels`, default off, EN + ES strings in
  js/strings-web.js. When on, an Item labels page joins the main pager
  between Create label and Insights, in dock order; swipe and back
  follow the enabled page list, and the pager offset is recomputed
  from that list so toggling cannot strand a page. When off, the app
  is the old two pages and nothing else changes.
- The page has three boxes (ITEM #, DESCRIPTION, QTY) and a Create
  button. Drafts survive view switches for the session and clear on
  Create, like the home drafts. Generating stays on the item page.
- The sheet is US Letter, 2 x 2, four identical cells of 3.5 x 5 in
  with a 0.5 in gutter (cells at x 36/324, y 36/396 in PDF points).
  Each label is drawn readable-first (5 x 3.5 in, 18 pt padding, logo
  324 pt wide on top, then ITEM / DESCRIPTION / QTY) and rotated 90
  degrees counter-clockwise into its cell via one graphics-state
  transform per cell. Row gap 8 pt; wrapped lines use a hanging
  indent (continuations align under the value). Font sizes: labels
  bold 20 pt, ITEM/QTY values 20 pt, DESCRIPTION value 18 pt.
- Assets are bundled for offline use and cached by the service
  worker: the Samara Vendrell Products letterhead
  (assets/logo_readable.jpg) and Tinos regular + bold
  (assets/fonts/Tinos-*.ttf, open licence, metric-compatible with
  Times New Roman). The print path is the same hidden-frame browser
  print flow as size labels, at 100% actual size.
- History is separate: store key `item_records`, PDF bytes in the
  same IndexedDB pdf store under the record id. Tap an entry to
  refill the boxes, Print reprints it (regenerating the PDF if its
  bytes are gone), Delete removes entry and bytes. Item records are
  not counted in Insights totals and are not touched by the 7-day
  cleanup. Identity is the exact ITEM | DESCRIPTION | QTY triple.
  (pdf-lib embeds custom TTFs only with a fontkit instance
  registered; the app bundles @pdf-lib/fontkit 1.1.1 as
  js/fontkit.umd.min.js. Quirk of the bundled pdf-lib build:
  registerFontkit is an instance method, so it runs on the
  document after create(), not as a static before it.)

## 13. Dot-pager arrows and keyboard (v1.7.1)

- The Settings and Info pagers share one helper (makePager). Besides
  touch swipes, they answer to the left/right arrow keys and to
  on-screen edge arrows (‹ ›) that sit at the left and right edges of
  the overlay. Edge arrows are hidden on phones (CSS display none)
  and shown in the wide layout only; they disable themselves at the
  first and last page.
- Arrow keys only act while their overlay is the current view, and
  never when the event target is a field or editable element, so
  typing is never hijacked. Buttons are not excluded on purpose:
  after opening Settings with the gear button, focus sits on that
  button, and excluding buttons would leave the keys dead for the
  mouse-first flow this is for.
- The item labels button gets the same wide-layout pill treatment as
  the home Create button (centered, auto width, min-width 16rem);
  phone portrait layout is unchanged.
