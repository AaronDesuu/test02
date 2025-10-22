package com.example.meterkenshin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import kotlin.random.Random

/**
 * Test ViewModel for simulating various ModernMeterCard states
 * This simulates different meter conditions and inspection states for UI testing
 */
class MeterTestViewModel : ViewModel() {

    private val _testMeters = MutableStateFlow<List<Meter>>(emptyList())
    val testMeters: StateFlow<List<Meter>> = _testMeters.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    init {
        generateTestMeters()
    }

    /**
     * Generate comprehensive test data covering all ModernMeterCard states
     */
    fun generateTestMeters() {
        val testMetersList = mutableListOf<Meter>()

        // 1. Active meter with full data (Green - Inspected & Billing Printed)
        testMetersList.add(
            Meter(
                uid = 0,
                activate = 1,
                serialNumber = "2286",
                location = "Residential Block A",
                type = MeterType.Type01,
                status = MeterStatus.ACTIVE,
                installationDate = Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000), // 1 year ago
                readDate = Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000), // 30 days ago
                bluetoothId = "48:23:35:0E:47:E3",
                fixedDate = Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000),
                impKWh = 1234.5,
                expKWh = 45.2,
                impMaxDemandKW = 12.8,
                expMaxDemandKW = 2.1,
                minVoltV = 220.5,
                alert = 0.0
            )
        )

        // 2. Active meter - Orange (Inspected, Billing not Printed)
        testMetersList.add(
            Meter(
                uid = 0,
                activate = 1,
                serialNumber = "2287",
                location = "Commercial Center B",
                type = MeterType.Type01,
                status = MeterStatus.ACTIVE,
                installationDate = Date(System.currentTimeMillis() - 200L * 24 * 60 * 60 * 1000),
                readDate = Date(System.currentTimeMillis() - 15L * 24 * 60 * 60 * 1000),
                bluetoothId = "48:23:35:10:47:BC",
                fixedDate = Date(System.currentTimeMillis() - 200L * 24 * 60 * 60 * 1000),
                impKWh = 2567.8,
                expKWh = 123.4,
                impMaxDemandKW = 25.6,
                expMaxDemandKW = 5.8,
                minVoltV = 215.3,
                alert = 1.0 // Has alert
            )
        )

        // 3. Offline meter - Red (Not Inspected)
        testMetersList.add(
            Meter(
                uid = 0,
                activate = 1,
                serialNumber = "2288",
                location = "Industrial Zone C",
                type = MeterType.Type01,
                status = MeterStatus.OFFLINE,
                installationDate = Date(System.currentTimeMillis() - 500L * 24 * 60 * 60 * 1000),
                readDate = null, // Never maintained
                bluetoothId = "48:23:35:0E:33:CF",
                fixedDate = Date(System.currentTimeMillis() - 500L * 24 * 60 * 60 * 1000),
                impKWh = null, // No readings available
                expKWh = null,
                impMaxDemandKW = null,
                expMaxDemandKW = null,
                minVoltV = null,
                alert = 2.0 // Critical alert
            )
        )

        // 4. Maintenance meter with partial data
        testMetersList.add(
            Meter(
                uid = 0,
                activate = 1,
                serialNumber = "2117",
                location = "Office Complex D",
                type = MeterType.Type01,
                status = MeterStatus.MAINTENANCE,
                installationDate = Date(System.currentTimeMillis() - 150L * 24 * 60 * 60 * 1000),
                readDate = Date(System.currentTimeMillis() - 5L * 24 * 60 * 60 * 1000),
                bluetoothId = "48:23:35:0E:CB:F3",
                fixedDate = Date(System.currentTimeMillis() - 150L * 24 * 60 * 60 * 1000),
                impKWh = 856.3,
                expKWh = 0.0, // No export
                impMaxDemandKW = 18.2,
                expMaxDemandKW = 0.0,
                minVoltV = 218.7,
                alert = 0.5 // Minor alert
            )
        )

        // 5. Error state meter
        testMetersList.add(
            Meter(
                uid = 0,
                activate = 1,
                serialNumber = "2300",
                location = "Hospital E",
                type = MeterType.Type01,
                status = MeterStatus.ERROR,
                installationDate = Date(System.currentTimeMillis() - 100L * 24 * 60 * 60 * 1000),
                readDate = Date(System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000),
                bluetoothId = "48:23:35:0E:55:AA",
                fixedDate = Date(System.currentTimeMillis() - 100L * 24 * 60 * 60 * 1000),
                impKWh = 3456.7,
                expKWh = 234.1,
                impMaxDemandKW = 45.8,
                expMaxDemandKW = 12.3,
                minVoltV = 195.2, // Low voltage
                alert = 3.0 // Critical alert
            )
        )

        // 6. High consumption meter (test max demand display)
        testMetersList.add(
            Meter(
                uid = 0,
                activate = 1,
                serialNumber = "2400",
                location = "Factory F",
                type = MeterType.Type01,
                status = MeterStatus.ACTIVE,
                installationDate = Date(System.currentTimeMillis() - 300L * 24 * 60 * 60 * 1000),
                readDate = Date(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000),
                bluetoothId = "48:23:35:0E:77:BB",
                fixedDate = Date(System.currentTimeMillis() - 300L * 24 * 60 * 60 * 1000),
                impKWh = 9999.9, // High consumption
                expKWh = 567.8,
                impMaxDemandKW = 99.9, // High max demand
                expMaxDemandKW = 25.4,
                minVoltV = 230.1,
                alert = 0.0
            )
        )

        // 7. New meter with minimal data
        testMetersList.add(
            Meter(
                uid = 0,
                activate = 1,
                serialNumber = "2500",
                location = "Residential G",
                type = MeterType.Type01,
                status = MeterStatus.ACTIVE,
                installationDate = Date(System.currentTimeMillis() - 1L * 24 * 60 * 60 * 1000), // Yesterday
                readDate = null, // New meter
                bluetoothId = "48:23:35:0E:88:CC",
                fixedDate = Date(System.currentTimeMillis() - 1L * 24 * 60 * 60 * 1000),
                impKWh = 12.3, // Minimal consumption
                expKWh = 0.0,
                impMaxDemandKW = 2.1,
                expMaxDemandKW = 0.0,
                minVoltV = 225.4,
                alert = 0.0
            )
        )

        // 8. Meter with export functionality (Solar/Generator)
        testMetersList.add(
            Meter(
                uid = 0,
                activate = 1,
                serialNumber = "2600",
                location = "Solar Home H",
                type = MeterType.Type01,
                status = MeterStatus.ACTIVE,
                installationDate = Date(System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000),
                readDate = Date(System.currentTimeMillis() - 20L * 24 * 60 * 60 * 1000),
                bluetoothId = "48:23:35:0E:99:DD",
                fixedDate = Date(System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000),
                impKWh = 1567.4,
                expKWh = 892.1, // High export (solar)
                impMaxDemandKW = 15.2,
                expMaxDemandKW = 18.7, // Higher export than import
                minVoltV = 235.8,
                alert = 0.0
            )
        )

        _testMeters.value = testMetersList
    }

    /**
     * Simulate real-time data updates for testing dynamic behavior
     */
    fun startLiveSimulation() {
        viewModelScope.launch {
            _isSimulating.value = true

            while (_isSimulating.value) {
                delay(3000) // Update every 3 seconds

                val updatedMeters = _testMeters.value.map { meter ->
                    when (meter.serialNumber) {
                        "1", "2", "4", "6", "7", "8" -> {
                            // Simulate active meter updates
                            meter.copy(
                                impKWh = (meter.impKWh ?: 0.0) + Random.nextDouble(0.1, 2.0),
                                expKWh = (meter.expKWh ?: 0.0) + Random.nextDouble(0.0, 0.5),
                                impMaxDemandKW = (meter.impMaxDemandKW ?: 0.0) + Random.nextDouble(-1.0, 1.0),
                                minVoltV = 220.0 + Random.nextDouble(-10.0, 15.0),
                                readDate = if (Random.nextBoolean()) Date() else meter.readDate
                            )
                        }
                        "3" -> {
                            // Simulate offline meter occasionally coming online
                            if (Random.nextDouble() < 0.1) { // 10% chance
                                meter.copy(
                                    status = MeterStatus.ACTIVE,
                                    impKWh = Random.nextDouble(100.0, 500.0),
                                    minVoltV = Random.nextDouble(210.0, 230.0)
                                )
                            } else meter
                        }
                        "5" -> {
                            // Simulate error meter state changes
                            val newStatus = if (Random.nextDouble() < 0.2) {
                                if (Random.nextBoolean()) MeterStatus.MAINTENANCE else MeterStatus.ACTIVE
                            } else MeterStatus.ERROR

                            meter.copy(
                                status = newStatus,
                                alert = Random.nextDouble(0.0, 3.0)
                            )
                        }
                        else -> meter
                    }
                }

                _testMeters.value = updatedMeters
            }
        }
    }

    /**
     * Stop live simulation
     */
    fun stopLiveSimulation() {
        _isSimulating.value = false
    }

    /**
     * Generate additional test meters for stress testing
     */
    fun generateStressTestMeters(count: Int = 50) {
        val existingMeters = _testMeters.value.toMutableList()

        repeat(count) { i ->
            existingMeters.add(
                Meter(
                    uid = 0,
                    activate = 1,
                    serialNumber = (3000 + i).toString(),
                    location = "Test Location ${('A'..'Z').random()}$i",
                    type = MeterType.entries.toTypedArray().random(),
                    status = MeterStatus.entries.toTypedArray().random(),
                    installationDate = Date(System.currentTimeMillis() - Random.nextLong(1, 1000) * 24 * 60 * 60 * 1000),
                    readDate = if (Random.nextBoolean()) Date(System.currentTimeMillis() - Random.nextLong(1, 100) * 24 * 60 * 60 * 1000) else null,
                    bluetoothId = "48:23:35:${String.format("%02X", Random.nextInt(256))}:${String.format("%02X", Random.nextInt(256))}:${String.format("%02X", Random.nextInt(256))}",
                    fixedDate = Date(System.currentTimeMillis() - Random.nextLong(1, 1000) * 24 * 60 * 60 * 1000),
                    impKWh = if (Random.nextBoolean()) Random.nextDouble(0.0, 10000.0) else null,
                    expKWh = if (Random.nextBoolean()) Random.nextDouble(0.0, 1000.0) else null,
                    impMaxDemandKW = if (Random.nextBoolean()) Random.nextDouble(1.0, 100.0) else null,
                    expMaxDemandKW = if (Random.nextBoolean()) Random.nextDouble(0.0, 50.0) else null,
                    minVoltV = if (Random.nextBoolean()) Random.nextDouble(190.0, 250.0) else null,
                    alert = Random.nextDouble(0.0, 3.0)
                )
            )
        }

        _testMeters.value = existingMeters
    }

    /**
     * Reset to original test data
     */
    fun resetTestData() {
        stopLiveSimulation()
        generateTestMeters()
    }

}