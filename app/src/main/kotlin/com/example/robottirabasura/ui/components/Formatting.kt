package com.example.robottirabasura.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local time of day, e.g. "14:03:27". */
fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

/** Day and time, e.g. "08/11 14:03". */
fun formatDateTime(millis: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(millis))
