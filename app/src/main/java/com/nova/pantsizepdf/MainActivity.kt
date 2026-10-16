package com.nova.pantsizepdf

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.*
import android.print.*
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var store: AppStore
    private lateinit var container: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var navDock: LinearLayout
    private lateinit var mainPager: ViewPager2
    private lateinit var settingsBtn: ImageView
    private lateinit var infoBtn: ImageView
    private var activePage: LinearLayout? = null
    private var palette = Palette.EMBER_NIGHT
    private var exportRecord: AppStore.LabelRecord? = null
    private val advancedInputs = mutableListOf<EditText>()
    private var savedInputText = ""
    private val savedAdvancedInputs = mutableMapOf<Int, String>()
    private var currentNav = 0
    private var lastBackPressTime = 0L
    private val createLock = Any()

    enum class Palette(
        val title: String,
        val lBg: String, val lSurf: String, val lAcc: String,
        val dBg: String, val dSurf: String, val dAcc: String
    ) {
        ROYAL_GRAPE("Midnight Purple", "#F4F4FC", "#E4E4F4", "#5E6AD2", "#05050A", "#0E0E1C", "#5E6AD2"),
        SPRING_PASTEL("Garden Meadow", "#F4FBF4", "#E2EFE2", "#2E7D32", "#040804", "#0C140C", "#81C784"),
        LAVENDER_CREAM("Petal Soft", "#FAF6FE", "#EDE2F7", "#7B1FA2", "#080509", "#150E1A", "#BA68C8"),
        EMBER_NIGHT("Crimson Dusk", "#FCF5F2", "#F5E4DC", "#D84315", "#060404", "#160D0A", "#FF7043"),
        BAUHAUS("Bold Primary", "#F0F0F0", "#FFFFFF", "#D02020", "#121212", "#1E1E1E", "#D02020"),
        TERMINAL("Hacker Green", "#0A0A0A", "#0A0A0A", "#33FF00", "#000000", "#050505", "#33FF00"),
        DOPAMINE("Neon Pop", "#0D0D1A", "#1C1C30", "#FF3AF2", "#05050A", "#0D0D1A", "#00F5D4")
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            try {
                contentResolver.openOutputStream(result.data!!.data!!)?.use { out ->
                    FileInputStream(store.fileFor(exportRecord!!)).use { it.copyTo(out) }
                }
                toast(getString(R.string.toast_exported))
            } catch (e: Exception) {
                toast(getString(R.string.toast_export_failed, e.message))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        store = AppStore(this)
        val lang = store.setting("lang", "system")
        if (lang != "system") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        }
        super.onCreate(savedInstanceState)
        
        AppCompatDelegate.setDefaultNightMode(when (store.setting("mode", "system")) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
        palette = runCatching { Palette.valueOf(store.setting("palette", Palette.EMBER_NIGHT.name)) }.getOrDefault(Palette.EMBER_NIGHT)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentNav != 0 || mainPager.currentItem != 0) {
                    showHome()
                } else {
                    val behavior = store.setting("back_behavior", "ask")
                    when (behavior) {
                        "one_press" -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                        "two_press" -> {
                            val now = System.currentTimeMillis()
                            if (now - lastBackPressTime < 2000) {
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed()
                                isEnabled = true
                            } else {
                                lastBackPressTime = now
                                toast(getString(R.string.toast_press_again_exit))
                            }
                        }
                        "disable" -> {
                            // Do nothing
                        }
                        else -> { // "ask"
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle(getString(R.string.dialog_exit_title))
                                .setMessage(getString(R.string.dialog_exit_msg))
                                .setPositiveButton(getString(R.string.btn_exit)) { _, _ ->
                                    isEnabled = false
                                    onBackPressedDispatcher.onBackPressed()
                                    isEnabled = true
                                }
                                .setNegativeButton(getString(R.string.btn_cancel), null)
                                .show()
                        }
                    }
                }
            }
        })
        buildShell()
        showHome()
    }

    override fun onResume() {
        super.onResume()
        if (::mainPager.isInitialized && mainPager.visibility == View.VISIBLE) {
            val current = mainPager.currentItem
            setupMainPager()
            mainPager.currentItem = current
        }
    }

    private fun isRetro(): Boolean = store.setting("ui_style", "modern") == "retro"
    private fun isBauhaus(): Boolean = store.setting("ui_style", "modern") == "bauhaus"
    private fun isTerminal(): Boolean = store.setting("ui_style", "modern") == "terminal"
    private fun isDopamine(): Boolean = store.setting("ui_style", "modern") == "dopamine"

    private fun buildShell() {
        container = FrameLayout(this).apply { setBackgroundColor(bg()) }

        content = FrameLayout(this).apply { 
            setPadding(0, if (isRetro()) dp(90) else dp(76), 0, dp(96)) 
            clipToPadding = false
        }
        container.addView(content, FrameLayout.LayoutParams(-1, -1))

        mainPager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            isSaveEnabled = false
            (getChildAt(0) as RecyclerView).overScrollMode = View.OVER_SCROLL_NEVER
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentNav = position
                    updateNavTint(position)
                }
            })
        }
        content.addView(mainPager, FrameLayout.LayoutParams(-1, -1))
        setupMainPager()

        if (isRetro()) {
            val titleBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor("#000080"), Color.parseColor("#1084D0")))
            }
            val titleText = TextView(this).apply {
                text = getString(R.string.retro_title)
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(Typeface.create("sans-serif", Typeface.BOLD), Typeface.BOLD)
            }
            titleBar.addView(titleText, LinearLayout.LayoutParams(0, -2, 1f))
            container.addView(titleBar, FrameLayout.LayoutParams(-1, dp(32)).apply { gravity = Gravity.TOP })
        }

        settingsBtn = ImageView(this).apply {
            setImageResource(resources.getIdentifier("ic_nav_settings", "drawable", packageName))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            if (!isRetro() && !isTerminal() && !isBauhaus()) elevation = dp(4).toFloat()
            setOnClickListener { 
                animateClick(this) { if (currentNav == 3) showHome() else showSettings() }
            }
        }
        val lpSettings = FrameLayout.LayoutParams(dp(46), dp(46)).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(dp(18), if (isRetro()) dp(38) else dp(18), 0, 0)
        }
        container.addView(settingsBtn, lpSettings)

        infoBtn = ImageView(this).apply {
            setImageResource(resources.getIdentifier("ic_nav_info", "drawable", packageName))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            if (!isRetro() && !isTerminal() && !isBauhaus()) elevation = dp(4).toFloat()
            setOnClickListener { 
                animateClick(this) { if (currentNav == 2) showHome() else showInfo() }
            }
        }
        val lpInfo = FrameLayout.LayoutParams(dp(46), dp(46)).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, if (isRetro()) dp(38) else dp(18), dp(18), 0)
        }
        container.addView(infoBtn, lpInfo)

        navDock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(6))
            background = when {
                isRetro() -> outsetRetro(surf())
                else -> round(surf(), 24)
            }
            if (!isRetro() && !isTerminal() && !isBauhaus()) elevation = dp(8).toFloat()
        }

        val createTab = ImageView(this).apply {
            setImageResource(resources.getIdentifier("ic_nav_create", "drawable", packageName))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { animateClick(this) { showHome() } }
        }
        navDock.addView(createTab, LinearLayout.LayoutParams(0, dp(46), 1f))

        val insightsTab = ImageView(this).apply {
            setImageResource(resources.getIdentifier("ic_nav_insights", "drawable", packageName))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { animateClick(this) { 
                if (currentNav == 1) showHome() else showInsights() 
            } }
        }
        navDock.addView(insightsTab, LinearLayout.LayoutParams(0, dp(46), 1f))

        val lpNav = FrameLayout.LayoutParams(if (isRetro()) dp(180) else dp(150), dp(56)).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, dp(24))
        }
        container.addView(navDock, lpNav)

        setContentView(container)
    }

    private fun setupMainPager() {
        mainPager.adapter = ViewPagerAdapter(listOf(homeView(), insightsView()))
    }

    private fun homeView(): View {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(16)) }
        val mascot = TextView(this).apply { text = when {
            isRetro() -> "📟"
            isTerminal() -> ">_"
            isBauhaus() -> "●"
            isDopamine() -> "✨"
            else -> "🐈‍⬛"
        }; textSize = 22f; gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(2)) }
        page.addView(mascot)

        ViewCompat.setOnApplyWindowInsetsListener(page) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()) || insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > dp(50)
            mascot.visibility = if (imeVisible) View.GONE else View.VISIBLE
            insets
        }

        val hideMascotOnFocus = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                mascot.visibility = View.GONE
            }
        }

        val layout = store.setting("layout", "4x15")
        page.addView(TextView(this).apply { text = getString(R.string.using_layout, layout); textSize = 18f; setTextColor(acc()); setTypeface(if (isTerminal()) Typeface.MONOSPACE else null, Typeface.BOLD); setPadding(0, dp(8), 0, dp(4)) })
        page.addView(TextView(this).apply { text = getString(R.string.privacy_note); textSize = 13f; setTextColor(textColor()); if (isTerminal()) setTypeface(Typeface.MONOSPACE); setPadding(0, 0, 0, dp(16)) })
        val input = EditText(this).apply { 
            hint = getString(R.string.hint_example)
            textSize = 16f
            setSingleLine(true)
            setTextColor(textColor())
            setHintTextColor(muted())
            if (isTerminal()) setTypeface(Typeface.MONOSPACE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = when {
                isRetro() -> insetRetro(if (isDark()) surf() else Color.WHITE)
                else -> round(if (isDark()) surf() else Color.WHITE, 10)
            }
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(savedInputText)
            if (savedInputText.isNotEmpty()) setSelection(savedInputText.length)
            onFocusChangeListener = hideMascotOnFocus
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    savedInputText = s?.toString() ?: ""
                }
            })
        }
        page.addView(input, LinearLayout.LayoutParams(-1, dp(48)))

        val isAdvEnabled = store.setting("advanced_mode", "false") == "true"
        val advContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (isAdvEnabled) View.VISIBLE else View.GONE
            setPadding(0, dp(4), 0, dp(4))
        }

        val jumpBtn = Button(this).apply {
            text = getString(R.string.jump_to_previous_unsaved)
            textSize = 12f
            setTextColor(acc())
            background = round(surf(), 8)
            visibility = View.GONE
        }

        val updateAdvancedValidation = {
            val vals = advancedInputs.map { it.text.toString().trim() }
            var firstBlankBeforeFilled = -1
            for (i in vals.indices) {
                if (vals[i].isNotEmpty()) {
                    for (j in 0 until i) {
                        if (vals[j].isEmpty()) {
                            if (firstBlankBeforeFilled == -1) firstBlankBeforeFilled = j
                        }
                    }
                }
            }
            for (i in advancedInputs.indices) {
                var hasBlankBefore = false
                for (k in i + 1 until vals.size) {
                    if (vals[k].isNotEmpty() && vals[i].isEmpty()) {
                        hasBlankBefore = true
                        break
                    }
                }
                val baseHint = getString(R.string.label_column_x, i + 1)
                advancedInputs[i].hint = if (hasBlankBefore) "$baseHint *" else baseHint
            }
            if (firstBlankBeforeFilled != -1) {
                jumpBtn.visibility = View.VISIBLE
                jumpBtn.setOnClickListener {
                    if (firstBlankBeforeFilled in advancedInputs.indices) {
                        advancedInputs[firstBlankBeforeFilled].requestFocus()
                    }
                }
            } else {
                jumpBtn.visibility = View.GONE
            }
        }
        
        if (isAdvEnabled) {
            input.visibility = View.GONE
            val layoutStr = store.setting("layout", "4x15")
            val layoutParts = layoutStr.split("x")
            val cols = layoutParts[0].toInt()
            
            advancedInputs.clear()
            val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until cols) {
                val initialVal = savedAdvancedInputs[i] ?: ""
                val colInput = EditText(this).apply {
                    hint = getString(R.string.label_column_x, i + 1)
                    textSize = 12f
                    setSingleLine(true)
                    setTextColor(textColor())
                    setHintTextColor(muted())
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    gravity = Gravity.CENTER
                    background = when {
                        isRetro() -> insetRetro(if (isDark()) surf() else Color.WHITE)
                        else -> round(if (isDark()) surf() else Color.WHITE, 8)
                    }
                    if (isTerminal()) setTypeface(Typeface.MONOSPACE)
                    setText(initialVal)
                    if (initialVal.isNotEmpty()) setSelection(initialVal.length)
                    onFocusChangeListener = hideMascotOnFocus
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            savedAdvancedInputs[i] = s?.toString() ?: ""
                            updateAdvancedValidation()
                        }
                    })
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                            if (text.isEmpty() && i > 0) {
                                advancedInputs[i - 1].requestFocus()
                                advancedInputs[i - 1].setSelection(advancedInputs[i - 1].text.length)
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                }
                grid.addView(colInput, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                advancedInputs.add(colInput)
            }
            advContainer.addView(grid)
            val jp = LinearLayout.LayoutParams(-1, dp(36))
            jp.setMargins(0, dp(8), 0, dp(4))
            advContainer.addView(jumpBtn, jp)
            updateAdvancedValidation()
        }
        page.addView(advContainer)

        val create = Button(this).apply { 
            text = getString(R.string.create_label_btn)
            textSize = 14f
            setTextColor(if (isRetro() || isBauhaus()) Color.BLACK else if (isTerminal()) acc() else bg())
            if (isTerminal()) setTypeface(Typeface.MONOSPACE)
            background = when {
                isRetro() -> outsetRetro(Color.parseColor("#C0C0C0"))
                isBauhaus() -> round(acc(), 0).apply { setStroke(dp(4), Color.BLACK) }
                isTerminal() -> round(Color.BLACK, 0).apply { setStroke(dp(1), acc()) }
                else -> round(acc(), 10)
            }
            setOnClickListener { 
                val size = if (isAdvEnabled) {
                    val vals = advancedInputs.map { it.text.toString().trim() }
                    if (vals.any { it.isEmpty() }) { toast(getString(R.string.err_invalid_column)); return@setOnClickListener }
                    vals.joinToString("|")
                } else {
                    input.text.toString()
                }
                createLabel(size) 
            } 
        }
        val cp = LinearLayout.LayoutParams(-1, dp(48)); cp.setMargins(0, dp(12), 0, dp(16)); page.addView(create, cp)
        page.addView(heading(getString(R.string.quick_print_title)))
        val isLastUsed = store.setting("quick_print_sort", "last") == "last"
        val records = store.records().filter { it.printCount > 0 }
        val frequent = if (isLastUsed) { records.sortedByDescending { it.lastPrintedAt } } else { records.sortedByDescending { it.printCount } }.distinctBy { store.normalizeSize(it.size) }.take(8)
        if (frequent.isEmpty()) page.addView(TextView(this).apply { text = getString(R.string.no_recent_labels); textSize = 13f; setTextColor(muted()); if (isTerminal()) setTypeface(Typeface.MONOSPACE); setPadding(dp(4), dp(4), 0, dp(12)) })
        frequent.forEach { record ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(8)); background = if (isRetro()) outsetRetro(surf()) else round(surf(), 10) }
            val displaySize = record.size.replace("|", " | ")
            val label = TextView(this).apply { text = displaySize; textSize = 15f; setTextColor(textColor()); setTypeface(if (isTerminal()) Typeface.MONOSPACE else null, Typeface.BOLD) }
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply { text = getString(R.string.print_count_text, record.printCount); textSize = 12f; setTextColor(muted()); if (isTerminal()) setTypeface(Typeface.MONOSPACE) })
            row.setOnClickListener { createLabel(record.size) }
            val rp = LinearLayout.LayoutParams(-1, dp(48)); rp.setMargins(0, 0, 0, dp(6)); page.addView(row, rp)
        }
        scroll.addView(page)
        return scroll
    }

    private fun insightsView(): View {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), dp(24)) }
        activePage = page
        page.addView(heading(getString(R.string.insights_title))); page.addView(body(getString(R.string.insights_sub)))
        val total = store.totalPrinted(); val unique = store.uniqueCreatedCount(); val all = store.records(); val top = all.groupBy { it.size }.mapValues { it.value.sumOf { r -> r.printCount } }.toList().sortedByDescending { it.second }.take(5)
        statCard(getString(R.string.stat_total_printed), total.toString(), getString(R.string.stat_total_printed_desc))
        statCard(getString(R.string.stat_unique_pdfs), unique.toString(), getString(R.string.stat_unique_pdfs_desc))
        statCard(getString(R.string.stat_saved_pdfs), all.size.toString(), getString(R.string.stat_saved_pdfs_desc))
        page.addView(heading(getString(R.string.most_printed_title)))
        if (top.isEmpty()) page.addView(body(getString(R.string.no_history)))
        top.forEachIndexed { i, item -> 
            val displaySize = item.first.replace("|", " | ")
            page.addView(body(getString(R.string.history_item_format, i + 1, displaySize, item.second))) 
        }
        page.addView(body(getString(R.string.storage_used, formatBytes(store.cacheSize()))))
        scroll.addView(page)
        return scroll
    }

    private fun showHome() {
        currentNav = 0
        content.removeAllViews()
        if (mainPager.parent == null) content.addView(mainPager)
        setupMainPager()
        mainPager.visibility = View.VISIBLE
        mainPager.currentItem = 0
        updateNavTint(0)
    }

    private fun showInsights() {
        currentNav = 1
        content.removeAllViews()
        if (mainPager.parent == null) content.addView(mainPager)
        setupMainPager()
        mainPager.visibility = View.VISIBLE
        mainPager.currentItem = 1
        updateNavTint(1)
    }

    private fun animateClick(view: View, onEnd: () -> Unit) {
        if (isRetro() || isTerminal() || isBauhaus()) {
            view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).withEndAction { onEnd() }.start()
            }.start()
            return
        }
        view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(120).withEndAction {
            view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).withEndAction { onEnd() }.start()
        }.start()
    }

    private fun setupPager(pages: List<View>) {
        val pager = ViewPager2(this).apply {
            adapter = ViewPagerAdapter(pages)
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            isSaveEnabled = false
            (getChildAt(0) as RecyclerView).overScrollMode = View.OVER_SCROLL_NEVER
        }
        val indicator = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(8)) }
        val dots = List(pages.size) { 
            View(this).apply {
                val size = dp(8)
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(dp(6), 0, dp(6), 0) }
                background = round(muted(), 4)
            }
        }
        dots.forEach { indicator.addView(it) }
        val updateDots = { position: Int ->
            dots.forEachIndexed { i, dot ->
                val active = i == position
                dot.background = round(if (active) acc() else muted(), 4)
                dot.animate().scaleX(if (active) 1.2f else 1.0f).scaleY(if (active) 1.2f else 1.0f).setDuration(200).start()
            }
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateDots(position) }
        })
        updateDots(0)
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(pager, LinearLayout.LayoutParams(-1, 0, 1f)); addView(indicator, LinearLayout.LayoutParams(-1, -2)) }
        content.addView(wrap, FrameLayout.LayoutParams(-1, -1))
    }

    private class ViewPagerAdapter(private val views: List<View>) : RecyclerView.Adapter<ViewPagerAdapter.ViewHolder>() {
        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = views[viewType]
            v.layoutParams = ViewGroup.LayoutParams(-1, -1)
            (v.parent as? ViewGroup)?.removeView(v)
            return ViewHolder(v)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {}
        override fun getItemCount(): Int = views.size
        override fun getItemViewType(position: Int): Int = position
    }

    private fun updateNavTint(activeIndex: Int) {
        val createTab = navDock.getChildAt(0) as? ImageView
        val insightsTab = navDock.getChildAt(1) as? ImageView
        createTab?.setColorFilter(if (activeIndex == 0) acc() else mutedColor())
        insightsTab?.setColorFilter(if (activeIndex == 1) acc() else mutedColor())
        settingsBtn.setColorFilter(if (activeIndex == 3) acc() else mutedColor())
        settingsBtn.background = when {
            isRetro() -> if (activeIndex == 3) insetRetro(accSurf()) else outsetRetro(surf())
            else -> round(if (activeIndex == 3) accSurf() else surf(), 20)
        }
        infoBtn.setColorFilter(if (activeIndex == 2) acc() else mutedColor())
        infoBtn.background = when {
            isRetro() -> if (activeIndex == 2) insetRetro(accSurf()) else outsetRetro(surf())
            else -> round(if (activeIndex == 2) accSurf() else surf(), 20)
        }
    }

    private fun mutedColor() = if (isRetro()) { if (isDark()) Color.parseColor("#404080") else Color.parseColor("#000080") } else (if (isDark()) Color.parseColor("#8A8F98") else Color.parseColor("#7A6B75"))
    private fun accSurf() = if (isDark()) Color.parseColor("#1C1A30") else Color.parseColor("#E4E6FA")

    private fun createLabel(raw: String) {
        val size = raw.trim()
        if (size.isEmpty()) { toast(getString(R.string.err_empty_size)); return }
        try {
            synchronized(createLock) {
                val existing = store.findRecordForSize(size)
                if (existing != null) {
                    val file = store.fileFor(existing)
                    if (file.exists() && file.length() > 0) {
                        printPdf(existing, file)
                        showHome()
                        return
                    }
                }
                val id = System.currentTimeMillis()
                val fileName = store.fileNameForSize(size)
                val record = AppStore.LabelRecord(id, size, fileName, id)
                val file = store.fileFor(record)
                val layout = store.setting("layout", "4x15").split("x")
                val cols = layout[0].toInt(); val rows = layout[1].toInt()
                FileOutputStream(file).use { PdfGenerator.generate(size.split("|"), it, cols, rows) }
                store.add(record)
                store.recordCreation(size)
                printPdf(record, file)
                showHome()
            }
        } catch (e: Exception) { toast(getString(R.string.err_create_fail, e.message)) }
    }

    private fun printPdf(record: AppStore.LabelRecord, file: File) {
        val manager = getSystemService(PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
                if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
                callback.onLayoutFinished(PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(), true)
            }
            override fun onWrite(pages: Array<PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
                try {
                    FileInputStream(file).use { input -> FileOutputStream(destination.fileDescriptor).apply { input.copyTo(this); flush() } }
                    if (!cancellationSignal.isCanceled) { 
                        store.markPrinted(record.id)
                        runOnUiThread { 
                            if (mainPager.visibility == View.VISIBLE) {
                                val current = mainPager.currentItem
                                setupMainPager()
                                mainPager.currentItem = current
                            }
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    }
                    else callback.onWriteCancelled()
                } catch (e: Exception) { callback.onWriteFailed(e.message) }
                finally { destination.close() }
            }
        }
        manager.print("Pant Size · ${record.size}", adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.NA_LETTER).build())
    }

    private fun showInfo() {
        currentNav = 2
        updateNavTint(2)
        content.removeAllViews()
        mainPager.visibility = View.GONE
        val p1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(24)) }
        p1.addView(heading(getString(R.string.manual_title)))
        p1.addView(body(getString(R.string.manual_welcome)))
        p1.addView(subheading(getString(R.string.manual_concept_title)))
        p1.addView(body(getString(R.string.manual_concept_body)))
        val p2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(24)) }
        p2.addView(subheading(getString(R.string.manual_steps_title)))
        p2.addView(body(getString(R.string.manual_steps_body)))
        val p3 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(24)) }
        p3.addView(subheading(getString(R.string.manual_settings_title)))
        p3.addView(body(getString(R.string.manual_settings_body)))
        val pages = listOf(p1, p2, p3).map { ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(it) } }
        setupPager(pages)
    }

    private fun showSettings() {
        currentNav = 3
        updateNavTint(3)
        content.removeAllViews()
        mainPager.visibility = View.GONE
        val p1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(24)) }
        p1.addView(heading(getString(R.string.section_utilities)))
        settingItem(p1, getString(R.string.setting_sort_format), if (store.setting("quick_print_sort", "last") == "last") getString(R.string.sort_last_used) else getString(R.string.sort_freq_used)) {
            val current = store.setting("quick_print_sort", "last")
            store.setSetting("quick_print_sort", if (current == "last") "freq" else "last")
            setupMainPager(); showSettings()
        }
        val backBehavior = store.setting("back_behavior", "ask")
        val backBehaviorTitle = when (backBehavior) {
            "one_press" -> getString(R.string.back_behavior_one_press)
            "two_press" -> getString(R.string.back_behavior_two_press)
            "disable" -> getString(R.string.back_behavior_disable)
            else -> getString(R.string.back_behavior_ask)
        }
        settingItem(p1, getString(R.string.setting_back_behavior), backBehaviorTitle) {
            chooseBackBehavior()
        }
        settingItem(p1, getString(R.string.setting_language), if (store.setting("lang", "system") == "system") getString(R.string.mode_system) else if (store.setting("lang", "system") == "es") getString(R.string.lang_es) else getString(R.string.lang_en)) {
            chooseLanguage()
        }
        val layoutStr = store.setting("layout", "4x15")
        val cols = layoutStr.split("x")[0].toInt()
        val isAdv = store.setting("advanced_mode", "false") == "true"
        val modeTitle = if (isAdv) getString(R.string.print_mode_advanced) else getString(R.string.print_mode_classic)
        val modeSub = if (isAdv) getString(R.string.print_mode_advanced_sub, cols, cols) else getString(R.string.print_mode_classic_sub)
        settingItem(p1, getString(R.string.setting_print_mode), "$modeTitle ($modeSub)") {
            store.setSetting("advanced_mode", if (isAdv) "false" else "true")
            setupMainPager(); showSettings()
        }
        settingItem(p1, getString(R.string.setting_layout), layoutStr) { chooseLayout() }
        settingItem(p1, getString(R.string.setting_export), getString(R.string.setting_export_sub)) { chooseExport() }
        val p2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(24)) }
        p2.addView(heading(getString(R.string.section_storage)))
        switchItem(p2, getString(R.string.setting_cleanup), getString(R.string.setting_cleanup_sub), store.setting("automatic_cleanup", "true") == "true") { 
            store.setSetting("automatic_cleanup", it.toString())
            if (it) store.performAutomaticCleanup()
        }
        settingItem(p2, getString(R.string.setting_delete_cache), getString(R.string.setting_delete_cache_sub)) { store.clearCache(); toast(getString(R.string.toast_cache_cleared)) }
        settingItem(p2, getString(R.string.setting_delete_old), getString(R.string.setting_delete_old_sub)) { chooseDeleteAge() }
        settingItem(p2, getString(R.string.setting_reset), getString(R.string.setting_reset_sub)) {
            AlertDialog.Builder(this).setTitle(getString(R.string.dialog_reset_title)).setMessage(getString(R.string.dialog_reset_msg)).setPositiveButton(getString(R.string.btn_reset)) { _, _ -> store.resetStats(); toast(getString(R.string.toast_stats_reset)); setupMainPager(); showHome() }.setNegativeButton(getString(R.string.btn_cancel), null).show()
        }
        val p3 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(24)) }
        p3.addView(heading(getString(R.string.section_appearance)))
        settingItem(p3, getString(R.string.setting_theme_mode), when(store.setting("mode", "system")) { "light"->getString(R.string.mode_light) "dark"->getString(R.string.mode_dark) else->getString(R.string.mode_system) }) { chooseMode() }
        settingItem(p3, getString(R.string.setting_theme_palette), palette.title) { choosePalette() }
        settingItem(p3, getString(R.string.setting_theme_style), when(store.setting("ui_style", "modern")) {
            "retro" -> getString(R.string.style_retro)
            "bauhaus" -> getString(R.string.style_bauhaus)
            "terminal" -> getString(R.string.style_terminal)
            "dopamine" -> getString(R.string.style_dopamine)
            else -> getString(R.string.style_modern)
        }) { chooseUiStyle() }
        val pages = listOf(p1, p2, p3).map { ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(it) } }
        setupPager(pages)
    }

    private fun settingItem(parent: LinearLayout, title: String, sub: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)); background = if (isRetro()) outsetRetro(surf()) else round(surf(), 10); setOnClickListener { animateClick(this) { onClick() } } }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(textColor()); setTypeface(if (isTerminal()) Typeface.MONOSPACE else null, Typeface.BOLD) })
        col.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(muted()); if (isTerminal()) setTypeface(Typeface.MONOSPACE) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply { text = "›"; textSize = 18f; setTextColor(muted()) })
        val p = LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(6)); parent.addView(row, p)
    }

    private fun switchItem(parent: LinearLayout, title: String, sub: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)); background = if (isRetro()) outsetRetro(surf()) else round(surf(), 10) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(textColor()); setTypeface(if (isTerminal()) Typeface.MONOSPACE else null, Typeface.BOLD) })
        col.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(muted()); if (isTerminal()) setTypeface(Typeface.MONOSPACE) })
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f)); val sw = Switch(this).apply { isChecked = checked; setOnCheckedChangeListener { _, isChecked -> onCheckedChange(isChecked) } }; row.addView(sw)
        val p = LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(6)); parent.addView(row, p)
    }

    private fun chooseLayout() {
        val options = arrayOf("4 x 15", "3 x 10", "4 x 20", "2 x 10")
        val values = arrayOf("4x15", "3x10", "4x20", "2x10")
        val current = values.indexOf(store.setting("layout", "4x15"))
        AlertDialog.Builder(this).setTitle(getString(R.string.setting_layout)).setSingleChoiceItems(options, current) { d, w -> store.setSetting("layout", values[w]); d.dismiss(); showSettings() }.show()
    }
    private fun chooseBackBehavior() {
        val options = arrayOf(
            getString(R.string.back_behavior_ask),
            getString(R.string.back_behavior_one_press),
            getString(R.string.back_behavior_two_press),
            getString(R.string.back_behavior_disable)
        )
        val values = arrayOf("ask", "one_press", "two_press", "disable")
        val current = values.indexOf(store.setting("back_behavior", "ask"))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setting_back_behavior))
            .setSingleChoiceItems(options, current) { d, w ->
                store.setSetting("back_behavior", values[w])
                d.dismiss()
                showSettings()
            }
            .show()
    }
    private fun chooseMode() { val values = arrayOf(getString(R.string.mode_system), getString(R.string.mode_light), getString(R.string.mode_dark)); val current = listOf("system", "light", "dark").indexOf(store.setting("mode", "system")); AlertDialog.Builder(this).setTitle(getString(R.string.setting_theme_mode)).setSingleChoiceItems(values, current) { d, w -> store.setSetting("mode", listOf("system", "light", "dark")[w]); d.dismiss(); recreate() }.show() }
    private fun choosePalette() { val values = Palette.entries.map { it.title }.toTypedArray(); val current = Palette.entries.indexOf(palette); AlertDialog.Builder(this).setTitle(getString(R.string.setting_theme_palette)).setSingleChoiceItems(values, current) { d, w -> store.setSetting("palette", Palette.entries[w].name); d.dismiss(); recreate() }.show() }
    private fun chooseUiStyle() { val options = arrayOf(getString(R.string.style_modern), getString(R.string.style_retro), getString(R.string.style_bauhaus), getString(R.string.style_terminal), getString(R.string.style_dopamine)); val values = arrayOf("modern", "retro", "bauhaus", "terminal", "dopamine"); val current = values.indexOf(store.setting("ui_style", "modern")); AlertDialog.Builder(this).setTitle(getString(R.string.setting_theme_style)).setSingleChoiceItems(options, current) { d, w -> store.setSetting("ui_style", values[w]); d.dismiss(); recreate() }.show() }
    private fun chooseDeleteAge() { val values = arrayOf(getString(R.string.delete_7_days), getString(R.string.delete_30_days), getString(R.string.delete_90_days), getString(R.string.delete_all)); AlertDialog.Builder(this).setTitle(getString(R.string.setting_delete_old)).setItems(values) { _, w -> val cutoff = if (w == 3) Long.MAX_VALUE else System.currentTimeMillis() - longArrayOf(7, 30, 90)[w] * 86_400_000L; val count = if (w == 3) { val n = store.records().size; store.records().forEach { store.remove(it.id) }; store.pdfDirectory().listFiles()?.forEach { it.delete() }; n } else store.removeOlderThan(cutoff); toast(getString(R.string.toast_deleted, count, if (count == 1) "" else "s")); showSettings() }.show() }
    private fun chooseExport() { val records = store.records(); if (records.isEmpty()) { toast(getString(R.string.toast_no_pdfs)); return }; val labels = records.map { "${it.size} · ${DateFormat.getDateInstance().format(Date(it.createdAt))}" }.toTypedArray(); AlertDialog.Builder(this).setTitle(getString(R.string.setting_export)).setItems(labels) { _, which -> exportRecord = records[which]; exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/pdf"; putExtra(Intent.EXTRA_TITLE, records[which].fileName) }) }.show() }
    
    private fun chooseLanguage() {
        val options = arrayOf(getString(R.string.mode_system), getString(R.string.lang_en), getString(R.string.lang_es))
        val values = arrayOf("system", "en", "es")
        val current = values.indexOf(store.setting("lang", "system"))
        AlertDialog.Builder(this).setTitle(getString(R.string.setting_language)).setSingleChoiceItems(options, current) { d, w ->
            store.setSetting("lang", values[w])
            d.dismiss()
            AppCompatDelegate.setApplicationLocales(if (values[w] == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(values[w]))
            recreate()
        }.show()
    }

    private fun heading(text: String) = TextView(this).apply { this.text = text; textSize = 20f; setTypeface(if (isTerminal()) Typeface.MONOSPACE else null, Typeface.BOLD); setTextColor(acc()); setPadding(0, dp(6), 0, dp(8)) }
    private fun subheading(text: String) = TextView(this).apply { this.text = text; textSize = 15f; setTypeface(if (isTerminal()) Typeface.MONOSPACE else null, Typeface.BOLD); setTextColor(acc()); setPadding(dp(2), dp(10), dp(2), dp(4)) }
    private fun body(text: String) = TextView(this).apply { this.text = text; textSize = 13f; setTextColor(textColor()); if (isTerminal()) setTypeface(Typeface.MONOSPACE); setPadding(dp(4), dp(4), dp(4), dp(10)) }
    private fun statCard(label: String, value: String, detail: String) { val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)); background = when {
        isRetro() -> outsetRetro(surf())
        isBauhaus() -> round(surf(), 0).apply { setStroke(dp(4), Color.BLACK) }
        isTerminal() -> round(Color.BLACK, 0).apply { setStroke(dp(1), acc()) }
        isDopamine() -> round(surf(), 10).apply { setStroke(dp(3), acc()) }
        else -> round(surf(), 10)
    } }; row.addView(TextView(this).apply { text = label; textSize = 11f; setTextColor(muted()); if (isTerminal()) setTypeface(Typeface.MONOSPACE) }); row.addView(TextView(this).apply { text = value; textSize = 26f; setTypeface(if (isTerminal()) Typeface.MONOSPACE else null, Typeface.BOLD); setTextColor(acc()) }); row.addView(TextView(this).apply { text = detail; textSize = 12f; setTextColor(textColor()); if (isTerminal()) setTypeface(Typeface.MONOSPACE) }); val p = LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, dp(8)); activePage?.addView(row, p) }
    private fun c(value: String) = Color.parseColor(value)
    private fun bg() = when {
        isRetro() -> if (isDark()) c("#000020") else c("#C0C0C0")
        else -> c(if (isDark()) palette.dBg else palette.lBg)
    }
    private fun surf() = when {
        isRetro() -> if (isDark()) c("#000040") else c("#C0C0C0")
        else -> c(if (isDark()) palette.dSurf else palette.lSurf)
    }
    private fun acc() = when {
        isRetro() -> if (isDark()) c("#60A0FF") else c("#0000FF")
        else -> c(if (isDark()) palette.dAcc else palette.lAcc)
    }
    private fun round(color: Int, r: Int) = GradientDrawable().apply { 
        shape = GradientDrawable.RECTANGLE; setColor(color)
        when {
            isBauhaus() -> {
                cornerRadius = if (r > 15) dp(99).toFloat() else 0f
                setStroke(dp(4), Color.BLACK)
            }
            isTerminal() -> {
                cornerRadius = 0f
                setStroke(dp(1), acc(), dp(4).toFloat(), dp(4).toFloat())
            }
            isDopamine() -> {
                cornerRadius = dp(r).toFloat()
                setStroke(dp(3), acc())
            }
            else -> cornerRadius = dp(r).toFloat()
        }
    }
    private fun outsetRetro(color: Int): LayerDrawable { val base = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(color) }; val light = if (isDark()) c("#303060") else Color.WHITE; val shadow = if (isDark()) c("#000010") else Color.parseColor("#808080"); val pLight = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setStroke(dp(2), light) }; val pShadow = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setStroke(dp(2), shadow) }; val ld = LayerDrawable(arrayOf(base, pLight, pShadow)); ld.setLayerInset(1, 0, 0, dp(2), dp(2)); ld.setLayerInset(2, dp(2), dp(2), 0, 0); return ld }
    private fun insetRetro(color: Int): LayerDrawable { val base = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(color) }; val shadow = if (isDark()) c("#000010") else Color.parseColor("#808080"); val light = if (isDark()) c("#303060") else Color.WHITE; val pShadow = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setStroke(dp(2), shadow) }; val pLight = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setStroke(dp(2), light) }; val ld = LayerDrawable(arrayOf(base, pShadow, pLight)); ld.setLayerInset(1, 0, 0, dp(2), dp(2)); ld.setLayerInset(2, dp(2), dp(2), 0, 0); return ld }
    private fun textColor() = if (isRetro()) { if (isDark()) Color.WHITE else Color.BLACK } else (if (isDark()) Color.WHITE else Color.parseColor("#1C1625"))
    private fun muted() = if (isRetro()) { if (isDark()) Color.parseColor("#A0A0A0") else Color.parseColor("#808080") } else (if (isDark()) Color.parseColor("#8A8F98") else Color.parseColor("#63535C"))
    private fun isDark(): Boolean { val mode = store.setting("mode", "system"); return if (mode == "system") (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES else mode == "dark" }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun formatBytes(bytes: Long): String = if (bytes < 1024) "$bytes B" else if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "${bytes / (1024 * 1024)} MB"
}
