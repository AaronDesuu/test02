package com.example.meterkenshin.ui.viewmodel

enum class SortField {
    SERIAL_NUMBER,
    LOCATION,
    LAST_MAINTENANCE_DATE
}

enum class SortOrder {
    ASCENDING,
    DESCENDING
}

data class SortConfig(
    val field: SortField = SortField.SERIAL_NUMBER,
    val order: SortOrder = SortOrder.ASCENDING
)