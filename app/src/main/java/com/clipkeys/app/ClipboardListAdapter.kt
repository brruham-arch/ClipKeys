package com.clipkeys.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class ClipboardListAdapter(
    context: Context,
    private val items: List<ClipboardItem>,
    private val onAction: (ClipboardItem, String) -> Unit
) : ArrayAdapter<ClipboardItem>(context, 0, items) {

    private val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = items[position]

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (item.isPinned) Color.parseColor("#1E2D1E") else Color.parseColor("#1E1E1E"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT, dp(52)
            )
        }

        // Text area (klik = paste)
        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onAction(item, "paste") }
        }

        val tvPin = TextView(context).apply {
            text = if (item.isPinned) "📌 " else ""
            visibility = if (item.isPinned) View.VISIBLE else View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(Color.parseColor("#FFB300"))
        }

        val tvText = TextView(context).apply {
            text = item.preview
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val tvTime = TextView(context).apply {
            text = sdf.format(Date(item.timestamp))
            setTextColor(Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        }

        textLayout.addView(tvPin)
        textLayout.addView(tvText)
        textLayout.addView(tvTime)
        row.addView(textLayout)

        // Action buttons
        val btnPin = makeActionBtn(if (item.isPinned) "📌" else "☆") { onAction(item, "pin") }
        val btnDel = makeActionBtn("🗑") { onAction(item, "delete") }
        row.addView(btnPin)
        row.addView(btnDel)

        // Divider bawah
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        wrapper.addView(row)
        val div = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#333333"))
        }
        wrapper.addView(div)
        return wrapper
    }

    private fun makeActionBtn(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { onClick() }
        }
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}
