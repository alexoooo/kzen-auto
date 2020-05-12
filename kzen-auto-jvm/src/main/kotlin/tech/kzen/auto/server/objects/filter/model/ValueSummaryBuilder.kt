package tech.kzen.auto.server.objects.filter.model

import com.google.common.collect.BoundType
import com.google.common.collect.HashMultiset
import com.google.common.collect.TreeMultiset
import tech.kzen.auto.common.paradigm.reactive.NominalValueSummary
import tech.kzen.auto.common.paradigm.reactive.NumericValueSummary
import tech.kzen.auto.common.paradigm.reactive.OpaqueValueSummary
import tech.kzen.auto.common.paradigm.reactive.ValueSummary


class ValueSummaryBuilder {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val maxNumericBuckets = 100
        private const val maxNominalBuckets = 250
        private const val maxSampleSize = 100
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


        private fun mergeNumeric(a: NumericValueSummary, b: NumericValueSummary): NumericValueSummary {
            // TODO: use HDR histogram?

            val builder = a.density.toMutableMap()

            fun intersect(start: Double, end: Double): Boolean {
                return builder.any { it.key.start <= end && it.key.endInclusive >= start }
            }

            for (e in b.density) {
                val existingCount = a.density[e.key] ?: 0
                if (existingCount != 0L) {
                    builder[e.key] = e.value + existingCount
                }
                else {
                    val start = e.key.start
                    val end = e.key.endInclusive
                    if (! intersect(start, end)) {
                        builder[e.key] = e.value
                    }
                    else {
                        val mid = (start + end) / 2
                        val container =
                            builder.filter { it.key.contains(mid) }.keys.firstOrNull()
                        if (container != null) {
                            builder[container] = builder[container]!! + e.value
                        }
                        else {
                            builder[start.rangeTo(end)] = e.value
                        }
                    }
                }
            }
            return NumericValueSummary(builder)
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
    private val numericHistogram = TreeMultiset.create<Double>()
    private var count: Long = 0


    //-----------------------------------------------------------------------------------------------------------------
    fun add(value: String) {
        val trimmed = value.trim()

        if (trimmed.isEmpty()) {
            return
        }

        count++

        val doubleValue = value.toDoubleOrNull()
        if (doubleValue == null) {
            textHistogram.add(trimmed)
        }
        else {
            numericHistogram.add(doubleValue)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun build(): ValueSummary {
        val nominal = buildNominal()
        val numeric = buildNumeric()
        val opaque = buildOpaque()

        return ValueSummary(
            count,
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
        if (textHistogram.isEmpty()) {
            return OpaqueValueSummary.empty
        }

        if (textHistogram.elementSet().size == 1) {
            return OpaqueValueSummary.empty
        }

        if (textHistogram.size != textHistogram.elementSet().size) {
            return OpaqueValueSummary.empty
        }

        val builder = mutableListOf<String>()

        for (e in textHistogram.entrySet()) {
            val value = e.element

            if (builder.size < maxSampleSize) {
                builder.add(value)
            }
            else {
                val randomIndex = (Math.random() * builder.size).toInt()
                builder[randomIndex] = value
            }
        }

        return OpaqueValueSummary(builder.toSet())
    }


    private fun buildNumeric(): NumericValueSummary {
        if (numericHistogram.isEmpty()) {
            return NumericValueSummary.empty
        }

        if (numericHistogram.elementSet().size == 1) {
            val singleValue = numericHistogram.elementSet().single()
            return NumericValueSummary(
                mapOf(singleValue.rangeTo(singleValue) to numericHistogram.size.toLong()))
        }

        val builder = mutableMapOf<ClosedFloatingPointRange<Double>, Long>()

        val min = numericHistogram.firstEntry().element
        val max = numericHistogram.lastEntry().element
        val buckets = numericHistogram.entrySet().size.coerceAtMost(maxNumericBuckets)
        val increment = (max - min) / buckets
        for (i in 0 until buckets) {
            val fromInclusive = min + i * increment
            val toExclusive = min + (i + 1) * increment
            val subset = numericHistogram.subMultiset(
                fromInclusive, BoundType.CLOSED, toExclusive, BoundType.OPEN)
            if (subset.isEmpty()) {
                continue
            }

            val subsetMin = subset.firstEntry().element
            val subsetMax = subset.lastEntry().element

            builder[subsetMin.rangeTo(subsetMax)] = subset.size.toLong()
        }

        return NumericValueSummary(builder)
    }
}