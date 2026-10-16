# Pantser (Pant Size Labeler)

Pantser is a premium, zero-maintenance native Android application designed specifically for apparel size printing on US Letter pages. It bridges the gap between quick size data entry and exact-spec PDF generation without file clutter.

---

## Key Features

### 1. Flexible Printing Modes
- **Classic Mode:** Type a single size (e.g., `32 x 30` or `M`) and generate a 4-column by 15-row grid.
- **Advanced Multi-Column Mode:** Configure custom column grids (e.g., 2, 3, or 4 columns) with individual size entries per column.
- **Smart Validation and Guidance:** 
  - **Skipped Size Asterisk (`*`):** Automatically flags blank preceding columns if you skip ahead.
  - **Jump Back to Unsaved Size:** Features a **"<- Jump to previous blank size"** button to instantly navigate to unconfirmed entries.
  - **Backspace Cell Navigation:** Pressing backspace on an empty cell automatically moves focus to the previous cell.

### 2. Intelligent Keyboard and Navigation UX
- **Auto-Stretching Home Screen:** When the software keyboard pops up, the header/mascot automatically collapses (`View.GONE`) and the layout resizes (`adjustResize`), ensuring the input fields and **"CREATE LABEL"** button are never blocked.
- **Persistent Input Memory:** Typed sizes are remembered across view state changes and tab switches, even if you accidentally click away.
- **Configurable Back Button Behavior:** 
  - Pressing back from secondary views (Insights, Info, Settings) returns you to the textbox.
  - On the homescreen, back button behavior is fully customizable in Settings: *Ask user* (confirmation dialog), *One press exit*, *Two press exit*, or *Disable exit*.

### 3. Quick Print Tray
- Recently printed dimensions are saved and displayed in a quick-print tray.
- Sortable by **Last Used** or **Frequently Used** for one-tap re-generation and printing.

### 4. Activity Insights and Analytics
- Track cumulative successful print jobs, unique PDF dimensions generated, current saved files in storage, and top 5 most printed sizes.
- Real-time storage usage monitoring and cache management.

### 5. 5 Unique UI Styles and 7 Custom Palettes
- Choose between 5 aesthetic design systems:
  - **Linear Modern** (Clean Material Design 3)
  - **Retro 90s Nostalgia** (Classic 3D bevels and Windows 95 aesthetics)
  - **Bauhaus Geometric** (Bold primary colors and stark black outlines)
  - **Terminal CLI** (Monospace hacker green aesthetic)
  - **Dopamine Maximalism** (Vibrant neon pops)
- Fully supports Dark and Light modes and 7 custom color palettes (Midnight Purple, Garden Meadow, Petal Soft, Crimson Dusk, Bold Primary, Hacker Green, Neon Pop).

### 6. Internationalization
- Fully localized in **English** and **Espanol**.

---

## How It Works (Architecture)

Pantser is built with pure, high-performance native Android components:

- **[`AppStore.kt`](app/src/main/java/com/pantser/landc/AppStore.kt):** Handles local-only persistence using `SharedPreferences` and manages generated PDF files in internal app storage (`filesDir`). Automatically cleans up duplicate records and removes files older than 7 days (configurable).
- **[`PdfGenerator.kt`](app/src/main/java/com/pantser/landc/PdfGenerator.kt):** Uses Android's native `PdfDocument` and `Canvas` APIs to draw exact US Letter dimensions (8.5 x 11 inches at 72 DPI), centering bold 24pt text perfectly inside table cells.
- **[`MainActivity.kt`](app/src/main/java/com/pantser/landc/MainActivity.kt):** Programmatically constructs the entire responsive view hierarchy, handling dynamic styling, viewpager navigation, back button dispatching, keyboard insets, and print job dispatching via Android's `PrintManager`.

---

## Building and Running

1. Open the `pant-size-pdf` project directory in **Android Studio**.
2. Wait for Gradle sync to complete.
3. Choose **Build -> Build App Bundle(s) / APK(s) -> Build APK(s)**.
4. The debug APK will be generated at:  
   `app/build/outputs/apk/debug/app-debug.apk`

*Minimum Android Version:* Android 10 (API 29). No external storage permissions required.
