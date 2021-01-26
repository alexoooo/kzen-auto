package tech.kzen.auto.common.util

import kotlin.math.log10
import kotlin.math.pow


object FormatUtils {
    // https://stackoverflow.com/a/5599842
    private val units = arrayOf("B", "kB", "MB", "GB", "TB")


    fun decimalSeparator(number: Long): String {
        return number
            .toString()
            .replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ",")
    }


    fun readableFileSize(size: Long): String {
        if (size <= 0) {
            return "0"
        }
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        val unitValue = size / 1024.0.pow(digitGroups.toDouble())
        val unitWhole = unitValue.toLong()
        val unitFraction = unitValue - unitWhole
        val wholeFormat = decimalSeparator(unitWhole)
        val unitFormat =
            if (unitFraction == 0.0) {
                wholeFormat
            }
            else {
                wholeFormat + "." + (unitFraction * 10).toLong()
            }
        return unitFormat + " " + units[digitGroups]
    }
}