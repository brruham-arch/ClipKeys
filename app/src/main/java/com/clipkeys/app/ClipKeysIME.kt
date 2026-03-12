package com.clipkeys.app

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*

class ClipKeysIME : InputMethodService() {

    private var isShifted = false
    private var isCapsLock = false
    private var isNumberMode = false
    private var isClipboardMode = false
    private var currentWord = StringBuilder()
    private var prevWord = ""

    private lateinit var rootView: LinearLayout
    private lateinit var suggestionBar: LinearLayout
    private lateinit var keyboardContainer: FrameLayout
    private lateinit var clipSearchEdit: EditText
    private lateinit var clipList: ListView

    private var clipboardManager: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString() ?: return@OnPrimaryClipChangedListener
        if (text.isNotBlank()) ClipboardStore.add(this, text)
    }

    override fun onCreate() {
        super.onCreate()
        ClipboardStore.init(this)
        WordPredictor.init(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        WordPredictor.save(this)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        suggestionBar = buildSuggestionBar()
        rootView.addView(suggestionBar)
        keyboardContainer = FrameLayout(this)
        keyboardContainer.addView(buildQwertyKeyboard())
        rootView.addView(keyboardContainer)
        return rootView
    }

    // ── SUGGESTION BAR ────────────────────────────────────────────────────────

    private val suggestionViews = mutableListOf<TextView>()

    private fun buildSuggestionBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#222222"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
            )
        }

        for (i in 0..4) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setBackgroundResource(android.R.drawable.list_selector_background)
                setPadding(dp(4), 0, dp(4), 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            tv.setOnClickListener { onSuggestionClick(tv.text.toString()) }
            bar.addView(tv)
            suggestionViews.add(tv)
            if (i < 4) {
                bar.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(Color.parseColor("#444444"))
                })
            }
        }
        return bar
    }

    private fun updateSuggestions() {
        val word = currentWord.toString()
        val (preds, corrections, nextWords) = WordPredictor.getSuggestions(word, prevWord)

        // Slot 0-2: prediksi prefix (biru muda)
        // Slot 3-4: koreksi typo (kuning) atau next word (hijau)
        val allSuggestions = mutableListOf<Pair<String, String>>() // text, type

        if (word.isEmpty()) {
            // Tampilkan next word suggestions
            nextWords.forEach { allSuggestions.add(it to "next") }
        } else {
            preds.forEach { allSuggestions.add(it to "pred") }
            corrections.forEach { allSuggestions.add(it to "corr") }
        }

        for (i in 0..4) {
            val tv = suggestionViews[i]
            val item = allSuggestions.getOrNull(i)
            if (item == null) {
                tv.text = ""; tv.setTextColor(Color.parseColor("#666666"))
            } else {
                tv.text = item.first
                tv.setTextColor(when (item.second) {
                    "pred" -> Color.WHITE
                    "corr" -> Color.parseColor("#FFD54F") // kuning = koreksi
                    "next" -> Color.parseColor("#81C784") // hijau = next word
                    else -> Color.WHITE
                })
            }
        }
    }

    private fun onSuggestionClick(word: String) {
        if (word.isEmpty()) return
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }
        ic.commitText("$word ", 1)
        WordPredictor.learnWord(word)
        prevWord = word
        currentWord.clear()
        updateSuggestions()
    }

    // ── KEYBOARD BUILDER ──────────────────────────────────────────────────────

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

    private fun buildQwertyKeyboard() = buildRows(QWERTY_ROWS)
    private fun buildNumberKeyboard() = buildRows(NUMBER_ROWS)

    private fun buildRows(rows: List<List<String>>): LinearLayout {
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
                rowLayout.addView(makeKey(key, when (key) {
                    "SPACE" -> 4f; "SHIFT","DEL","ENTER","?123","ABC" -> 1.5f; "📋" -> 1.2f; else -> 1f
                }))
            }
            container.addView(rowLayout)
        }
        return container
    }

    private fun makeKey(key: String, weight: Float): TextView {
        val isSpecial = key in listOf("SHIFT","DEL","?123","ABC","ENTER")
        val bgRes = when {
            key == "SPACE" || key == "ENTER" -> R.drawable.key_accent_bg
            isSpecial -> R.drawable.key_special_bg
            else -> R.drawable.key_bg
        }
        val label = when (key) {
            "SPACE" -> "space"; "DEL" -> "⌫"; "ENTER" -> "↩"
            "SHIFT" -> if (isCapsLock) "⇪" else if (isShifted) "⇧●" else "⇧"
            else -> if (isShifted || isCapsLock) key.uppercase() else key.lowercase()
        }
        return TextView(this).apply {
            text = label; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isSpecial || key == "SPACE") 12f else 16f)
            setTypeface(typeface, if (isSpecial) Typeface.BOLD else Typeface.NORMAL)
            setBackgroundResource(bgRes)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            isClickable = true; isFocusable = false
            setOnClickListener { handleKey(key) }
            if (key == "DEL") setOnLongClickListener { deleteWord(); true }
        }
    }

    // ── KEY HANDLER ───────────────────────────────────────────────────────────

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return
        when (key) {
            "DEL" -> {
                ic.deleteSurroundingText(1, 0)
                if (currentWord.isNotEmpty()) currentWord.deleteCharAt(currentWord.length - 1)
                updateSuggestions()
            }
            "SHIFT" -> {
                when { isCapsLock -> { isCapsLock = false; isShifted = false }; isShifted -> isCapsLock = true; else -> isShifted = true }
                rebuildKeyboard()
            }
            "SPACE" -> {
                if (currentWord.isNotEmpty()) {
                    WordPredictor.learnWord(currentWord.toString())
                    prevWord = currentWord.toString()
                }
                currentWord.clear()
                ic.commitText(" ", 1)
                updateSuggestions()
            }
            "ENTER" -> {
                if (currentWord.isNotEmpty()) { WordPredictor.learnWord(currentWord.toString()); prevWord = currentWord.toString() }
                currentWord.clear()
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED)
                    ic.performEditorAction(action)
                else ic.commitText("\n", 1)
                updateSuggestions()
            }
            "?123" -> { isNumberMode = true; rebuildKeyboard() }
            "ABC" -> { isNumberMode = false; rebuildKeyboard() }
            "📋" -> {
                isClipboardMode = true
                keyboardContainer.removeAllViews()
                keyboardContainer.addView(buildClipboardPanel())
            }
            else -> {
                val char = if (isShifted || isCapsLock) key.uppercase() else key.lowercase()
                ic.commitText(char, 1)
                if (char.all { it.isLetter() || it == '\'' }) currentWord.append(char)
                else {
                    if (currentWord.isNotEmpty()) { WordPredictor.learnWord(currentWord.toString()); prevWord = currentWord.toString() }
                    currentWord.clear()
                }
                if (isShifted && !isCapsLock) { isShifted = false; rebuildKeyboard() }
                updateSuggestions()
            }
        }
    }

    private fun rebuildKeyboard() {
        if (isClipboardMode) return
        keyboardContainer.removeAllViews()
        keyboardContainer.addView(if (isNumberMode) buildNumberKeyboard() else buildQwertyKeyboard())
    }

    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(100, 0)?.toString() ?: return
        val trimmed = before.trimEnd()
        val lastSpace = trimmed.lastIndexOf(' ')
        val count = if (lastSpace < 0) trimmed.length else trimmed.length - lastSpace
        if (count > 0) ic.deleteSurroundingText(count, 0)
        currentWord.clear(); updateSuggestions()
    }

    // ── CLIPBOARD PANEL ───────────────────────────────────────────────────────

    private fun buildClipboardPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#222222"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        clipSearchEdit = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            hint = "🔍 Cari clipboard..."; setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#333333"))
            setPadding(dp(8), 0, dp(8), 0); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isSingleLine = true; showSoftInputOnFocus = false
        }
        val btnClose = TextView(this).apply {
            text = "⌨"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { isClipboardMode = false; rebuildKeyboard() }
        }
        topBar.addView(clipSearchEdit); topBar.addView(btnClose); panel.addView(topBar)

        clipList = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160))
            divider = null; dividerHeight = 0
        }
        refreshClipList("")
        panel.addView(clipList)

        clipSearchEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refreshClipList(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        return panel
    }

    private fun refreshClipList(query: String) {
        val items = ClipboardStore.search(query)
        clipList.adapter = ClipboardListAdapter(this, items) { item, action ->
            when (action) {
                "paste" -> {
                    currentInputConnection?.commitText(item.text, 1)
                    isClipboardMode = false; rebuildKeyboard()
                }
                "pin" -> { ClipboardStore.togglePin(this, item.id); refreshClipList(clipSearchEdit.text.toString()) }
                "delete" -> { ClipboardStore.delete(this, item.id); refreshClipList(clipSearchEdit.text.toString()) }
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
