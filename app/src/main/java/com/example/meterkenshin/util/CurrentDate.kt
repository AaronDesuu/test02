package com.example.meterkenshin.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun getCurrentDate(
    @Suppress("SameParameterValue") monthOffset: Int,
    dayOffset: Int
): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MONTH, monthOffset)
    calendar.add(Calendar.DAY_OF_MONTH, dayOffset)
    val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return format.format(calendar.time)
}

fun getCurrentDateTime(): String {
    val format = SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss", Locale.getDefault())
    return format.format(Date())
}

fun getCurrentYearMonth(): String {
    val sdf = SimpleDateFormat("yyyyMM", Locale.getDefault())
    return sdf.format(Date())
}