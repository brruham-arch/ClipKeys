package com.clipkeys.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.Context
import android.view.*
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClipboardStore.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        root.addView(TextView(this).apply {
            text = "⌨ ClipKeys"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(dp(16), dp(16), dp(16), dp(8))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        // Buttons row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), dp(8))
        }

        fun btn(text: String, color: String, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
            setBackgroundColor(Color.parseColor(color))
            setTextColor(Color.WHITE)
            setOnClickListener { onClick() }
        }

        btnRow.addView(btn("⚙ Set Keyboard", "#1976D2") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            Toast.makeText(this, "Aktifkan 'ClipKeys' lalu pilih sebagai keyboard", Toast.LENGTH_LONG).show()
        })
        btnRow.addView(btn("📤 Export", "#388E3C") {
            if (ClipboardStore.export(this)) {
                Toast.makeText(this, "✅ Tersimpan di Downloads", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ Gagal export", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(btnRow)

        // Stats
        root.addView(TextView(this).apply {
            text = "📋 ${ClipboardStore.count()} item tersimpan (unlimited)"
            setTextColor(Color.parseColor("#AAAAAA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(16), 0, dp(16), dp(8))
        })

        // Search
        val searchEdit = EditText(this).apply {
            hint = "🔍 Cari..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), 0, dp(12), dp(8)) }
        }
        root.addView(searchEdit)

        // List
        val listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            divider = null
        }
        root.addView(listView)

        setContentView(root)

        fun refresh(q: String = "") {
            val items = ClipboardStore.search(q).toMutableList()
            val adapter = ClipboardListAdapter(this, items) { item, action ->
                when (action) {
                    "paste" -> {
                        // Di main app, paste = copy ke clipboard
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("clip", item.text))
                        Toast.makeText(this, "✅ Disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                    }
                    "pin" -> { ClipboardStore.togglePin(this, item.id); refresh(searchEdit.text.toString()) }
                    "delete" -> {
                        AlertDialog.Builder(this)
                            .setTitle("Hapus item?")
                            .setMessage(item.preview)
                            .setPositiveButton("Hapus") { _, _ -> ClipboardStore.delete(this, item.id); refresh(searchEdit.text.toString()) }
                            .setNegativeButton("Batal", null).show()
                    }
                }
            }
            listView.adapter = adapter
        }

        refresh()

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { refresh(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
