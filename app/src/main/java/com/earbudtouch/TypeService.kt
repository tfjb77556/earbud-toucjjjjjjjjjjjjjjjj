package com.earbudtouch

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Inserts text into the focused text field of whatever app is open. */
class TypeService : AccessibilityService() {
    companion object {
        @Volatile var instance: TypeService? = null
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun focused(): AccessibilityNodeInfo? {
        val n = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        return if (n.isEditable) n else null
    }

    private fun setText(n: AccessibilityNodeInfo, text: String, cursor: Int) {
        val a = Bundle()
        a.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, a)
        val s = Bundle()
        s.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
        s.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, s)
    }

    private fun current(n: AccessibilityNodeInfo): Triple<String, Int, Int> {
        val cur = if (n.isShowingHintText) "" else (n.text?.toString() ?: "")
        var st = n.textSelectionStart
        var en = n.textSelectionEnd
        if (st < 0 || en < 0) { st = cur.length; en = cur.length }
        if (st > en) { val t = st; st = en; en = t }
        return Triple(cur, st.coerceIn(0, cur.length), en.coerceIn(0, cur.length))
    }

    fun insert(s: String): Boolean {
        val n = focused() ?: return false
        val (cur, st, en) = current(n)
        setText(n, cur.substring(0, st) + s + cur.substring(en), st + s.length)
        return true
    }

    fun back(): Boolean {
        val n = focused() ?: return false
        val (cur, st, en) = current(n)
        if (st != en) setText(n, cur.substring(0, st) + cur.substring(en), st)
        else if (st > 0) setText(n, cur.substring(0, st - 1) + cur.substring(en), st - 1)
        return true
    }
}
