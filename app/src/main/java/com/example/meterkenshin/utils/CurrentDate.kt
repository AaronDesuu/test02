package com.example.meterkenshin.utils

import android.annotation.SuppressLint
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

private val MonthList = arrayOf(
    "----", "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

/**
 * Convert datetime string to "Month Year" format
 * Input: yyyy/MM/dd HH:mm:ss
 * Output: "September 2024"
 */
fun dateTimeToMonth(dateTime: String): String {
    val monthIndex = dateTime.substring(5, 7).toInt()
    val year = dateTime.substring(0, 4)
    return "${MonthList[monthIndex]} $year"
}

/**
 * Convert datetime to MM/dd/yyyy format
 * Input: yyyy/MM/dd HH:mm:ss
 * Output: MM/dd/yyyy
 */
fun convertLocalDatetime(yearMonDay: String): String {
    return String.format(
        "%s/%s/%s",
        yearMonDay.substring(5, 7),  // Month
        yearMonDay.substring(8, 10), // Day
        yearMonDay.substring(0, 4)   // Year
    )
}

/**
 * Format month and day offset from current date
 * Returns format: "Month DD, YYYY"
 */
@SuppressLint("DefaultLocale")
fun formattedMonthDay(monthOffset: Int, dayOffset: Int): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.MONTH, monthOffset)
    calendar.add(Calendar.DATE, dayOffset)

    val monthIndex = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DATE)
    val year = calendar.get(Calendar.YEAR)

    return String.format("%s %2d, %4d", MonthList[monthIndex], day, year)
}

/**
 * Format month and day offset from a base date
 * Returns format: "Month DD, YYYY"
 * @param baseDate the reference date to offset from
 */
@SuppressLint("DefaultLocale")
fun formattedMonthDay(baseDate: Date, monthOffset: Int, dayOffset: Int): String {
    val calendar = Calendar.getInstance()
    calendar.time = baseDate
    calendar.add(Calendar.MONTH, monthOffset)
    calendar.add(Calendar.DATE, dayOffset)

    val monthIndex = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DATE)
    val year = calendar.get(Calendar.YEAR)

    return String.format("%s %2d, %4d", MonthList[monthIndex], day, year)
}