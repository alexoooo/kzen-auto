package tech.kzen.auto.server.objects.filter.model

import com.google.common.collect.HashMultiset
import tech.kzen.auto.common.paradigm.reactive.NominalValueSummary
import tech.kzen.auto.common.paradigm.reactive.OpaqueValueSummary
import tech.kzen.auto.common.paradigm.reactive.StatisticValueSummary
import tech.kzen.auto.common.paradigm.reactive.ValueSummary


class ValueSummaryBuilder {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        private const val maxNumericBuckets = 100
        private const val maxNominalBuckets = 250
        private const val maxSampleSize = 100
        private const val sampleThreshold = 10_000
        private const val nominalOther = "<other>"


        fun merge(a: ValueSummary, b: ValueSummary): ValueSummary {
            return ValueSummary(
                a.count + b.count,
                mergeNominal(a.nominalValueSummary, b.nominalValueSummary),
                mergeNumeric(a.numericValueSummary, b.numericValueSummary),
                mergeOpaque(a.opaqueValueSummary, b.opaqueValueSummary)
            )
        }


        private fun mergeNominal(a: NominalValueSummary, b: NominalValueSummary): NominalValueSummary {
            val builder = a.histogram.toMutableMap()
            for (e in b.histogram) {
                val existingCount = a.histogram[e.key] ?: 0
                builder[e.key] = e.value + existingCount
            }
            return NominalValueSummary(builder)
        }


        private fun mergeNumeric(a: StatisticValueSummary, b: StatisticValueSummary): StatisticValueSummary {
            // TODO: use HDR histogram?
            return StatisticValueSummary(
                a.count + b.count,
                a.sum + b.sum,
                a.min.coerceAtMost(b.min),
                a.max.coerceAtLeast(b.max)
            )
        }


        private fun mergeOpaque(a: OpaqueValueSummary, b: OpaqueValueSummary): OpaqueValueSummary {
            val builder = a.sample.toMutableList()

            val iterator = b.sample.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (builder.size < maxSampleSize) {
                    builder.add(next)
                }
                else {
                    val randomIndex = (Math.random() * builder.size).toInt()
                    builder[randomIndex] = next
                }
            }

            return OpaqueValueSummary(builder.toSet())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val textHistogram = HashMultiset.create<String>()
    private val textSample = mutableListOf<String>()

//    private val numericHistogram = TreeMultiset.create<Double>()
    private var emptyCount: Long = 0
    private var textCount: Long = 0
    private var numberCount: Long = 0
    private var sum: Double = 0.0
    private var min: Double = Double.POSITIVE_INFINITY
    private var max: Double = Double.NEGATIVE_INFINITY


    //-----------------------------------------------------------------------------------------------------------------
    fun add(value: String) {
        val trimmed = value.trim()

        if (trimmed.isEmpty()) {
            emptyCount++
            return
        }

        if (isSimpleNumber(trimmed)) {
            val doubleValue = java.lang.Double.parseDouble(trimmed)

            numberCount++
            sum += doubleValue
            min = min.coerceAtMost(doubleValue)
            max = max.coerceAtLeast(doubleValue)
        }
        else {
            textCount++

            if (textSample.isNotEmpty()) {
                val randomIndex = (Math.random() * textSample.size).toInt()
                textSample[randomIndex] = trimmed
            }
            else {
                textHistogram.add(trimmed)

                if (textHistogram.elementSet().size > sampleThreshold) {
                    val iterator = textHistogram.elementSet().iterator()
                    while (textSample.size < maxSampleSize) {
                        textSample.add(iterator.next())
                    }
                    textHistogram.clear()
                }
            }
        }
    }


    @Suppress("ConvertTwoComparisonsToRangeCheck")
    private fun isSimpleNumber(text: String): Boolean {
        var dotCount = 0;
        var digitCount = 0;
        var alphaCount = 0;

        var i = 0
        val length = text.length
        while (i < length) {
            val char = text[i++]
            if (char == '.') {
                dotCount++
            }
            else if ('0' <= char && char <= '9') {
                digitCount++
            }
            else {
                alphaCount++
            }
        }

        return digitCount > 0 && dotCount <= 1 && alphaCount == 0
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun build(): ValueSummary {
        val nominal = buildNominal()
        val numeric = buildNumeric()
        val opaque = buildOpaque()

        return ValueSummary(
            textCount + numberCount,
            nominal,
            numeric,
            opaque)
    }


    private fun buildNominal(): NominalValueSummary {
        if (textHistogram.isEmpty()) {
            return NominalValueSummary.empty
        }

        if (textHistogram.elementSet().size == 1) {
            val only = textHistogram.elementSet().first()
            return NominalValueSummary(
                mapOf(only to textHistogram.size.toLong()))
        }

        if (textHistogram.size == textHistogram.elementSet().size) {
            return NominalValueSummary.empty
        }

        val builder = mutableMapOf<String, Long>()

        for (e in textHistogram.entrySet()) {
            builder[e.element] = e.count.toLong()
        }

        var otherCount = 0L
        while (builder.size > maxNominalBuckets) {
            val minCount = builder.entries.minBy { it.value }!!.value
            val iterator = builder.iterator()
            while (iterator.hasNext() && builder.size > maxNominalBuckets) {
                val next = iterator.next()
                if (next.value == minCount) {
                    iterator.remove()
                    otherCount++
                }
            }
        }

        if (otherCount > 0) {
            builder[nominalOther] = otherCount
        }

        return NominalValueSummary(builder)
    }


    private fun buildOpaque(): OpaqueValueSummary {
        if (textSample.isEmpty()) {
            return OpaqueValueSummary.empty
        }

        return OpaqueValueSummary(textSample.toSet())
    }


    private fun buildNumeric(): StatisticValueSummary {
        if (numberCount == 0L) {
            return StatisticValueSummary.empty
        }

        return StatisticValueSummary(
            numberCount, sum, min, max)
    }
}