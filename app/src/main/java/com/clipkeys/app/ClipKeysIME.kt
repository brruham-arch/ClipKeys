package com.clipkeys.app

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*

class ClipKeysIME : InputMethodService() {

    // State
    private var isShifted = false
    private var isCapsLock = false
    private var isNumberMode = false
    private var isClipboardMode = false
    private var currentWord = StringBuilder()

    // Views
    private lateinit var rootView: LinearLayout
    private lateinit var predictionBar: LinearLayout
    private lateinit var keyboardContainer: FrameLayout
    private lateinit var pred1: TextView
    private lateinit var pred2: TextView
    private lateinit var pred3: TextView

    // Clipboard panel views
    private lateinit var clipSearchEdit: EditText
    private lateinit var clipList: ListView
    private var clipAdapter: ClipboardListAdapter? = null

    // Clipboard monitoring
    private var clipboardManager: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: return@OnPrimaryClipChangedListener
        if (text.isNotBlank()) {
            ClipboardStore.add(this, text)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        ClipboardStore.init(this)
        WordPredictor.init(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        WordPredictor.saveLearnedWords(this)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        predictionBar = buildPredictionBar()
        rootView.addView(predictionBar)

        keyboardContainer = FrameLayout(this)
        keyboardContainer.addView(buildQwertyKeyboard())
        rootView.addView(keyboardContainer)

        return rootView
    }

    // ─────────────────────────────────────────
    // PREDICTION BAR
    // ─────────────────────────────────────────

    private fun buildPredictionBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#222222"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
            )
        }

        fun makePredView(): TextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundResource(android.R.drawable.list_selector_background)
        }

        pred1 = makePredView(); pred2 = makePredView(); pred3 = makePredView()

        // Dividers
        fun divider() = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#444444"))
        }

        bar.addView(pred1); bar.addView(divider())
        bar.addView(pred2); bar.addView(divider())
        bar.addView(pred3)

        pred1.setOnClickListener { commitPrediction(pred1.text.toString()) }
        pred2.setOnClickListener { commitPrediction(pred2.text.toString()) }
        pred3.setOnClickListener { commitPrediction(pred3.text.toString()) }

        return bar
    }

    private fun updatePredictions() {
        val word = currentWord.toString()
        if (word.isEmpty()) {
            pred1.text = ""; pred2.text = ""; pred3.text = ""; return
        }
        val preds = WordPredictor.predict(word)
        pred1.text = preds.getOrNull(0) ?: ""
        pred2.text = preds.getOrNull(1) ?: ""
        pred3.text = preds.getOrNull(2) ?: ""
    }

    private fun commitPrediction(word: String) {
        if (word.isEmpty()) return
        val ic = currentInputConnection ?: return
        // Hapus kata yang sedang diketik, ganti dengan prediksi
        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }
        ic.commitText("$word ", 1)
        WordPredictor.learnWord(word)
        currentWord.clear()
        updatePredictions()
    }

    // ─────────────────────────────────────────
    // KEYBOARD BUILDER
    // ─────────────────────────────────────────

    private val QWERTY_ROWS = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("SHIFT","z","x","c","v","b","n","m","DEL"),
        listOf("?123","📋","SPACE",".","ENTER")
    )

    private val NUMBER_ROWS = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","%","&","-","+","(",")","/"),
        listOf("=","*","\"","'",":",";","!","?","DEL"),
        listOf("ABC","📋","SPACE",",","ENTER")
    )

    private fun buildQwertyKeyboard() = buildKeyboardRows(QWERTY_ROWS)
    private fun buildNumberKeyboard() = buildKeyboardRows(NUMBER_ROWS)

    private fun buildKeyboardRows(rows: List<List<String>>): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
                ).apply { setMargins(0, dp(2), 0, dp(2)) }
            }

            for (key in row) {
                val weight = when (key) {
                    "SPACE" -> 4f
                    "SHIFT", "DEL", "ENTER", "?123", "ABC" -> 1.5f
                    "📋" -> 1.2f
                    else -> 1f
                }
                val keyView = makeKey(key, weight)
                rowLayout.addView(keyView)
            }
            container.addView(rowLayout)
        }
        return container
    }

    private fun makeKey(key: String, weight: Float): TextView {
        val isSpecial = key in listOf("SHIFT","DEL","?123","ABC","ENTER")
        val isSpace = key == "SPACE"
        val isClipboard = key == "📋"

        val bgRes = when {
            isSpace || key == "ENTER" -> R.drawable.key_accent_bg
            isSpecial -> R.drawable.key_special_bg
            else -> R.drawable.key_bg
        }

        val label = when (key) {
            "SPACE" -> "space"
            "DEL" -> "⌫"
            "ENTER" -> "↩"
            "SHIFT" -> if (isCapsLock) "⇪" else if (isShifted) "⇧●" else "⇧"
            else -> if (isShifted || isCapsLock) key.uppercase() else key.lowercase()
        }

        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isSpecial || isSpace) 12f else 16f)
            setTypeface(typeface, if (isSpecial) Typeface.BOLD else Typeface.NORMAL)
            setBackgroundResource(bgRes)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            isClickable = true
            isFocusable = false

            setOnClickListener {
                handleKey(key)
                // Update shift label after press
                if (key != "SHIFT") refreshShiftKey()
            }

            // Long press DEL = hapus kata
            if (key == "DEL") {
                setOnLongClickListener {
                    deleteWord(); true
                }
            }
        }
    }

    private var shiftKeyView: TextView? = null

    private fun refreshShiftKey() {
        // Rebuild keyboard to reflect shift state
        // Simple approach: rebuild keyboard rows
        if (!isClipboardMode && !isNumberMode) {
            keyboardContainer.removeAllViews()
            keyboardContainer.addView(buildQwertyKeyboard())
        }
    }

    // ─────────────────────────────────────────
    // KEY HANDLER
    // ─────────────────────────────────────────

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return

        when (key) {
            "DEL" -> {
                ic.deleteSurroundingText(1, 0)
                if (currentWord.isNotEmpty()) {
                    currentWord.deleteCharAt(currentWord.length - 1)
                    updatePredictions()
                }
            }
            "SHIFT" -> {
                when {
                    isCapsLock -> { isCapsLock = false; isShifted = false }
                    isShifted -> isCapsLock = true
                    else -> isShifted = true
                }
                refreshShiftKey()
            }
            "SPACE" -> {
                if (currentWord.isNotEmpty()) WordPredictor.learnWord(currentWord.toString())
                currentWord.clear()
                ic.commitText(" ", 1)
                updatePredictions()
            }
            "ENTER" -> {
                if (currentWord.isNotEmpty()) WordPredictor.learnWord(currentWord.toString())
                currentWord.clear()
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(action)
                } else {
                    ic.commitText("\n", 1)
                }
                updatePredictions()
            }
            "?123" -> {
                isNumberMode = true
                keyboardContainer.removeAllViews()
                keyboardContainer.addView(buildNumberKeyboard())
            }
            "ABC" -> {
                isNumberMode = false
                keyboardContainer.removeAllViews()
                keyboardContainer.addView(buildQwertyKeyboard())
            }
            "📋" -> {
                isClipboardMode = true
                keyboardContainer.removeAllViews()
                keyboardContainer.addView(buildClipboardPanel())
            }
            else -> {
                val char = if (isShifted || isCapsLock) key.uppercase() else key.lowercase()
                ic.commitText(char, 1)
                // Track current word
                if (char.all { it.isLetter() || it == '\'' }) {
                    currentWord.append(char)
                } else {
                    if (currentWord.isNotEmpty()) WordPredictor.learnWord(currentWord.toString())
                    currentWord.clear()
                }
                // Auto-unshift after one char (not caps lock)
                if (isShifted && !isCapsLock) {
                    isShifted = false
                    refreshShiftKey()
                }
                updatePredictions()
            }
        }
    }

    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
        // Hapus sampai spasi terakhir
        var count = 0
        val trimmed = before.trimEnd()
        val lastSpace = trimmed.lastIndexOf(' ')
        count = if (lastSpace < 0) trimmed.length else trimmed.length - lastSpace
        if (count > 0) ic.deleteSurroundingText(count, 0)
        currentWord.clear()
        updatePredictions()
    }

    // ─────────────────────────────────────────
    // CLIPBOARD PANEL
    // ─────────────────────────────────────────

    private fun buildClipboardPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        // Top bar: search + close
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#222222"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
            )
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        clipSearchEdit = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            hint = "🔍 Cari clipboard..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(dp(8), 0, dp(8), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isSingleLine = true
            // Jangan trigger IME saat ini aktif
            showSoftInputOnFocus = false
        }

        val btnClose = TextView(this).apply {
            text = "⌨"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener {
                isClipboardMode = false
                keyboardContainer.removeAllViews()
                keyboardContainer.addView(if (isNumberMode) buildNumberKeyboard() else buildQwertyKeyboard())
            }
        }

        topBar.addView(clipSearchEdit)
        topBar.addView(btnClose)
        panel.addView(topBar)

        // Clipboard list
        clipList = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(160)
            )
            divider = null
            dividerHeight = 0
        }

        refreshClipList("")
        panel.addView(clipList)

        // Search watcher
        clipSearchEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refreshClipList(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        return panel
    }

    private fun refreshClipList(query: String) {
        val items = ClipboardStore.search(query)
        clipAdapter = ClipboardListAdapter(this, items) { item, action ->
            when (action) {
                "paste" -> {
                    val ic = currentInputConnection ?: return@ClipboardListAdapter
                    ic.beginBatchEdit()
                    ic.commitText(item.text, 1) // Full text, unlimited!
                    ic.endBatchEdit()
                    // Tutup panel setelah paste
                    isClipboardMode = false
                    keyboardContainer.removeAllViews()
                    keyboardContainer.addView(if (isNumberMode) buildNumberKeyboard() else buildQwertyKeyboard())
                }
                "pin" -> {
                    ClipboardStore.togglePin(this, item.id)
                    refreshClipList(clipSearchEdit.text.toString())
                }
                "delete" -> {
                    ClipboardStore.delete(this, item.id)
                    refreshClipList(clipSearchEdit.text.toString())
                }
            }
        }
        clipList.adapter = clipAdapter
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
