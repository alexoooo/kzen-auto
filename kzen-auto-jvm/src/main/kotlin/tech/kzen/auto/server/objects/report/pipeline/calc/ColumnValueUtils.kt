package tech.kzen.auto.server.objects.report.pipeline.calc

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*


object ColumnValueUtils {
    //-----------------------------------------------------------------------------------------------------------------
    // see: https://stackoverflow.com/a/25307973/1941359
    // see: https://stackoverflow.com/questions/4387170/decimalformat-formatdouble-in-different-threads/38069338
    private val decimalFormat = ThreadLocal.withInitial {
        val df = DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH))
        df.maximumFractionDigits = 340
        df
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun formatDecimal(value: Double): String {
        if (! value.isFinite()) {
            return value.toString()
        }

        return decimalFormat.get().format(value)
    }
}