package com.example.meterkenshin.utils

import com.example.meterkenshin.model.Meter
import com.example.meterkenshin.ui.viewmodel.MeterReadingViewModel

object FilterUtils {

    // Filter functions for inspection status
    fun filterNotInspected(meters: List<Meter>): List<Meter> {
        return meters.filter {
            getInspectionStatus(it) == InspectionStatus.NOT_INSPECTED
        }
    }

    fun filterInspected(meters: List<Meter>): List<Meter> {
        return meters.filter {
            val status = getInspectionStatus(it)
            status == InspectionStatus.INSPECTED_BILLING_NOT_PRINTED ||
                    status == InspectionStatus.INSPECTED_BILLING_PRINTED
        }
    }

    fun filterBillingNotPrinted(meters: List<Meter>): List<Meter> {
        return meters.filter {
            getInspectionStatus(it) == InspectionStatus.INSPECTED_BILLING_NOT_PRINTED
        }
    }

    fun filterBillingPrinted(meters: List<Meter>): List<Meter> {
        return meters.filter {
            getInspectionStatus(it) == InspectionStatus.INSPECTED_BILLING_PRINTED
        }
    }

    // Filter functions for connection status
    fun filterOnline(meters: List<Meter>, viewModel: MeterReadingViewModel): List<Meter> {
        return meters.filter { viewModel.isMeterNearby(it.bluetoothId) }
    }

    fun filterOffline(meters: List<Meter>, viewModel: MeterReadingViewModel): List<Meter> {
        return meters.filter { !viewModel.isMeterNearby(it.bluetoothId) }
    }

    // Show all meters (no filter)
    fun clearFilters(meters: List<Meter>): List<Meter> {
        return meters
    }
}