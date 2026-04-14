package com.hstc.quicklogin.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugLogStore {
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun add(message: String) {
        val stamped = "${formatter.format(Date())}  $message"
        _lines.value = (_lines.value + stamped).takeLast(120)
    }
}
