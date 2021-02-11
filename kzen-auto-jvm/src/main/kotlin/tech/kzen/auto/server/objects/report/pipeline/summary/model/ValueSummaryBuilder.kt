package tech.kzen.auto.server.objects.report.pipeline.summary.model

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import tech.kzen.auto.common.objects.document.report.summary.ColumnSummary
import tech.kzen.auto.common.objects.document.report.summary.NominalValueSummary
import tech.kzen.auto.common.objects.document.report.summary.OpaqueValueSummary
import tech.kzen.auto.common.objects.document.report.summary.StatisticValueSummary
import tech.kzen.auto.server.objects.report.pipeline.input.model.RecordFieldFlyweight
import kotlin.random.Random


class ValueSummaryBuilder {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        private const val maxNominalBuckets = 100

        private const val maxNominalBuckets = 100
//        private const val maxNominalBuckets = 250

        private const val maxSampleSize = 100

//        private const val sampleThreshold = 250
        private const val sampleThreshold = 1_000
//        private const val sampleThreshold = 10_000

//        private const val histogramLengthThreshold = 64
        private const val histogramLengthThreshold = 128

        private const val nominalOther = "<other>"

        private const val sampleFrequency = 25
        private const val sampleSubProbability = 0.25


        fun merge(a: ColumnSummary, b: ColumnSummary): ColumnSummary {
            return ColumnSummary(
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
    private val textHistogram = Long2LongOpenHashMap()
    private val histogramValues = Long2ObjectLinkedOpenHashMap<String>()

    private val textSample = mutableListOf<String>()
    private val random = Random(System.nanoTime())

    private var histogramOverflow: Boolean = false
    private var valueCount: Long = 0
    private var emptyCount: Long = 0
    private var textCount: Long = 0
    private var numberCount: Long = 0
    private var sum: Double = 0.0
    private var min: Double = Double.POSITIVE_INFINITY
    private var max: Double = Double.NEGATIVE_INFINITY


    //-----------------------------------------------------------------------------------------------------------------
    fun add(value: RecordFieldFlyweight) {
        valueCount++

        if (value.isEmpty()) {
            emptyCount++

            if (! histogramOverflow) {
                addTextHistogramFlyweight(RecordFieldFlyweight.empty)
            }
            return
        }

        val doubleValue = value.toDoubleOrNan()
        if (! doubleValue.isNaN()) {
            numberCount++
            sum += doubleValue
            min = min.coerceAtMost(doubleValue)
            max = max.coerceAtLeast(doubleValue)

            addSample(value)
        }
        else {
            textCount++

            if (! histogramOverflow && value.length < histogramLengthThreshold) {
                addTextHistogramFlyweight(value)

                if (histogramValues.size > sampleThreshold) {
                    histogramValues.values.forEach(this::addSample)
                    textHistogram.clear()
                    histogramValues.clear()
                    histogramOverflow = true
                }
            }
            else {
                addSample(value)
            }
        }
    }


    private fun addTextHistogramFlyweight(value: RecordFieldFlyweight) {
        val signature = value.goodHash()
        val previousCount = textHistogram.addTo(signature, 1)

        if (previousCount == 0L) {
            histogramValues[signature] = value.toString()
        }
    }


    private fun addSample(value: RecordFieldFlyweight) {
        if (textSample.size < sampleThreshold) {
            textSample.add(value.toString())
        }
        else if (valueCount % sampleFrequency == 0L && random.nextFloat() < sampleSubProbability) {
            val randomIndex = random.nextInt(textSample.size)
            textSample[randomIndex] = value.toString()
        }
    }


    private fun addSample(value: String) {
        if (textSample.size < sampleThreshold) {
            textSample.add(value)
        }
        else if (valueCount % sampleFrequency == 0L && random.nextFloat() < sampleSubProbability) {
            val randomIndex = random.nextInt(textSample.size)
            textSample[randomIndex] = value
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun build(): ColumnSummary {
        val nominal = buildNominal()
        val numeric = buildNumeric()
        val opaque = buildOpaque()

        return ColumnSummary(
            textCount + numberCount,
            nominal,
            numeric,
            opaque)
    }


    private fun buildNominal(): NominalValueSummary {
        if (textHistogram.isEmpty()) {
            return NominalValueSummary.empty
        }

        if (histogramValues.size == 1) {
            val only = histogramValues.values.first()
            return NominalValueSummary(
                mapOf(only to textHistogram.size.toLong()))
        }

        val builder = mutableMapOf<String, Long>()

        for (e in textHistogram.long2LongEntrySet()) {
            val key = histogramValues[e.longKey]
            builder[key] = e.longValue
        }

        var otherCount = 0L
        while (builder.size > maxNominalBuckets) {
            val minCount = builder.entries.minByOrNull { it.value }!!.value
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