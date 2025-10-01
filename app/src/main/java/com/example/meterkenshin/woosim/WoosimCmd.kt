package com.example.meterkenshin.woosim

/**
 * Woosim Printer Commands
 * Enhanced with status checking commands for WSP-i350
 */
@Suppress("unused")
object WoosimCmd {

    // MCU Types
    const val MCU_RX = 0
    const val MCU_TX = 1

    // Code Tables
    const val CT_CP437 = 0
    const val CT_CP850 = 1
    const val CT_CP852 = 2
    const val CT_CP860 = 3
    const val CT_CP863 = 4
    const val CT_CP865 = 5
    const val CT_CP866 = 6
    const val CT_CP857 = 7
    const val CT_CP862 = 8
    const val CT_CP864 = 9
    const val CT_CP737 = 10
    const val CT_CP1252 = 11
    const val CT_CP1250 = 12
    const val CT_CP1251 = 13
    const val CT_CP1253 = 14
    const val CT_CP1254 = 15
    const val CT_CP1255 = 16
    const val CT_CP1256 = 17
    const val CT_CP1257 = 18
    const val CT_CP1258 = 19

    // Font Sizes
    const val FONT_SMALL = 0
    const val FONT_MEDIUM = 1
    const val FONT_LARGE = 2

    /**
     * Set code table command
     */
    fun setCodeTable(mcu: Int, codeTable: Int, fontSize: Int): ByteArray {
        return byteArrayOf(0x1B, 0x74, codeTable.toByte())
    }

    /**
     * Set text style command
     */
    fun setTextStyle(
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        widthMagnification: Int,
        heightMagnification: Int
    ): ByteArray {
        val cmd = mutableListOf<Byte>()

        // Set emphasis (bold)
        if (bold) {
            cmd.addAll(listOf(0x1B, 0x45, 0x01))
        } else {
            cmd.addAll(listOf(0x1B, 0x45, 0x00))
        }

        // Set character size
        val sizeCmd = ((widthMagnification - 1) shl 4) or (heightMagnification - 1)
        cmd.addAll(listOf(0x1D, 0x21, sizeCmd.toByte()))

        return cmd.toByteArray()
    }

    /**
     * Print and feed paper
     */
    fun printAndFeed(lines: Int = 1): ByteArray {
        return when (lines) {
            1 -> byteArrayOf(0x0A) // Line feed
            else -> byteArrayOf(0x1B, 0x64, lines.toByte()) // Print and feed n lines
        }
    }

    /**
     * Print in standard mode
     */
    fun PM_printStdMode(): ByteArray {
        return byteArrayOf(0x1B, 0x53) // ESC S
    }

    /**
     * Initialize printer
     */
    fun initPrinter(): ByteArray {
        return byteArrayOf(0x1B, 0x40) // ESC @
    }

    /**
     * Set alignment
     * @param alignment 0 = left, 1 = center, 2 = right
     */
    fun setAlignment(alignment: Int): ByteArray {
        return byteArrayOf(0x1B, 0x61, alignment.toByte())
    }

    /**
     * Cut paper
     */
    fun cutPaper(): ByteArray {
        return byteArrayOf(0x1D, 0x56, 0x42, 0x00) // Full cut
    }

    /**
     * Feed paper and cut
     */
    fun feedAndCut(lines: Int = 3): ByteArray {
        val cmd = mutableListOf<Byte>()
        // Feed lines
        for (i in 0 until lines) {
            cmd.add(0x0A)
        }
        // Cut paper
        cmd.addAll(cutPaper().toList())
        return cmd.toByteArray()
    }

    // ========== STATUS CHECK COMMANDS ==========

    /**
     * Request printer status (WSP-i350 specific)
     * WSP-i350 uses DLE EOT EOT (not DLE EOT n)
     * Returns M C P status: Mark sensor, Cover sensor, Paper sensor
     */
    fun requestPrinterStatus(): ByteArray {
        return byteArrayOf(0x10, 0x04, 0x04) // DLE EOT EOT
    }

    /**
     * Parse paper status from status byte (WSP-i350)
     * Bit 0: 1 = paper present, 0 = paper not present
     */
    fun isPaperPresent(statusByte: Byte): Boolean {
        val status = statusByte.toInt()
        return (status and 0x01) != 0
    }

    /**
     * Parse cover status from status byte (WSP-i350)
     * Bit 1: 1 = cover closed, 0 = cover opened
     */
    fun isCoverClosed(statusByte: Byte): Boolean {
        val status = statusByte.toInt()
        return (status and 0x02) != 0
    }

    /**
     * Parse mark sensor status from status byte (WSP-i350)
     * Bit 2: 1 = mark found, 0 = mark not found
     */
    fun isMarkFound(statusByte: Byte): Boolean {
        val status = statusByte.toInt()
        return (status and 0x04) != 0
    }

    /**
     * Get human-readable status message from status byte (WSP-i350)
     */
    fun getStatusMessage(statusByte: Byte): String {
        val status = statusByte.toInt()

        return buildString {
            // Bit 0: Paper sensor
            if ((status and 0x01) != 0) {
                append("Paper OK ")
            } else {
                append("Paper Out ")
            }

            // Bit 1: Cover sensor
            if ((status and 0x02) != 0) {
                append("Cover Closed ")
            } else {
                append("Cover Open ")
            }

            // Bit 2: Mark sensor
            if ((status and 0x04) != 0) {
                append("Mark Found")
            } else {
                append("No Mark")
            }
        }.trim()
    }

    /**
     * Enable/Disable automatic status back (ASB)
     * ESC c 3 n command
     */
    fun enableAutoStatusBack(enable: Boolean): ByteArray {
        val enableByte = if (enable) 0xFF.toByte() else 0x00.toByte()
        return byteArrayOf(0x1B, 0x63, 0x33, enableByte)
    }
}