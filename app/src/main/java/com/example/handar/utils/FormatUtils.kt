package com.example.handar.utils

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

fun formatDate(timestampMs: Long): String {
    if (timestampMs <= 0) return "--/--/----"

    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val date = Date(timestampMs)

    return formatter.format(date)
}