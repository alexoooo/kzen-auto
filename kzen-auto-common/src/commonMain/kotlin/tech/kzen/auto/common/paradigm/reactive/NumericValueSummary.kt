package tech.kzen.auto.common.paradigm.reactive

//
//data class NumericValueSummary(
//    val density: Map<ClosedFloatingPointRange<Double>, Long>
//) {
//    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        val empty = NumericValueSummary(mapOf())
//
//
//        fun fromCollection(collection: List<List<Any>>): NumericValueSummary {
//            return NumericValueSummary(
//                collection.map {
//                    val from = it[0] as Double
//                    val to = it[1] as Double
//                    val count = (it[2] as String).toLong()
//                    from.rangeTo(to) to count
//                }.toMap())
//        }
//
//
//        fun fromCsv(csv: List<List<String>>): NumericValueSummary {
//            val afterHeader = csv.subList(1, csv.size)
//            val density = afterHeader
//                .map {
//                    val from = it[0].toDouble()
//                    val to = it[1].toDouble()
//                    val count = it[2].toLong()
//                    from.rangeTo(to) to count
//                }
//                .toMap()
//            return NumericValueSummary(density)
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    fun isEmpty(): Boolean {
//        return density.isEmpty()
//    }
//
//
//    fun toCollection(): List<List<Any>> {
//        return density.entries.map {
//            listOf(it.key.start, it.key.endInclusive, it.value.toString())
//        }
//    }
//
//
//    fun toCsv(): List<List<String>> {
//        val builder = mutableListOf<List<String>>()
//
//        builder.add(listOf("From", "To", "Count"))
//
//        for (e in density) {
//            builder.add(listOf(e.key.start.toString(), e.key.endInclusive.toString(), e.value.toString()))
//        }
//
//        return builder
//    }
//}