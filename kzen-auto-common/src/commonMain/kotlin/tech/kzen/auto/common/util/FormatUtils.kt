package tech.kzen.auto.common.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Instant


object FormatUtils {
    // https://stackoverflow.com/a/5599842
    private val units = arrayOf("B", "kB", "MB", "GB", "TB")

    private const val maxAbbreviatedLength = 96
    private const val ellipsisSuffix = "…"


    fun sanitizeFilename(filenameFragment: String): String {
        return filenameFragment
            .replace(Regex("[^a-zA-Z0-9_-]+"), "_")
    }


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


    // A cell-width-bounded rendering of an arbitrary data value: whitespace-only carries no visible content, so
    // it is labelled instead. The suffix is a parameter because it counts against the same total width, so the
    // typographic ellipsis and the three-dot spelling keep a different amount of the original.
    fun abbreviateValue(value: String, abbreviationSuffix: String = ellipsisSuffix): String {
        if (value.isBlank()) {
            return "(blank)"
        }

        if (value.length < maxAbbreviatedLength) {
            return value
        }

        return value.substring(0, maxAbbreviatedLength - abbreviationSuffix.length) + abbreviationSuffix
    }


    fun formatLocalDateTime(time: Instant): String {
        val modifiedLocal = time.toLocalDateTime(TimeZone.currentSystemDefault())
        val hours = modifiedLocal.hour.toString().padStart(2, '0')
        val minutes = modifiedLocal.minute.toString().padStart(2, '0')
        val seconds = modifiedLocal.second.toString().padStart(2, '0')
        return "${modifiedLocal.date} $hours:$minutes:$seconds"
    }
}