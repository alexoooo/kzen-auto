package tech.kzen.auto.common.exec
//
//import tech.kzen.auto.common.paradigm.common.model.*
//import tech.kzen.lib.platform.IoUtils
//import kotlin.test.Test
//import kotlin.test.assertEquals
//
//
//class ExecutionValueTest {
//    @Test
//    fun nullValue() {
//        asCollectionEquals(mapOf(
//                "type" to "null"
//        ), NullExecutionValue)
//    }
//
//
//    @Test
//    fun emptyText() {
//        val value = ""
//        asCollectionEquals(mapOf(
//                "type" to "text",
//                "value" to value
//        ), TextExecutionValue(value))
//    }
//
//
//    @Test
//    fun simpleText() {
//        val value = "foo"
//        asCollectionEquals(mapOf(
//                "type" to "text",
//                "value" to value
//        ), TextExecutionValue(value))
//    }
//
//
//    @Test
//    fun booleanValues() {
//        asCollectionEquals(mapOf(
//                "type" to "boolean",
//                "value" to false
//        ), BooleanExecutionValue(false))
//
//        asCollectionEquals(mapOf(
//                "type" to "boolean",
//                "value" to true
//        ), BooleanExecutionValue(true))
//    }
//
//
//    @Test
//    fun numberValues() {
//        asCollectionEquals(mapOf(
//                "type" to "number",
//                "value" to 0.0
//        ), NumberExecutionValue(0.0))
//
//        asCollectionEquals(mapOf(
//                "type" to "number",
//                "value" to 1.0
//        ), NumberExecutionValue(1.0))
//
//        asCollectionEquals(mapOf(
//                "type" to "number",
//                "value" to -1.0
//        ), NumberExecutionValue(-1.0))
//
//        asCollectionEquals(mapOf(
//                "type" to "number",
//                "value" to Double.MIN_VALUE
//        ), NumberExecutionValue(Double.MIN_VALUE))
//
//        asCollectionEquals(mapOf(
//                "type" to "number",
//                "value" to Double.MAX_VALUE
//        ), NumberExecutionValue(Double.MAX_VALUE))
//
//        asCollectionEquals(mapOf(
//                "type" to "number",
//                "value" to Double.NEGATIVE_INFINITY
//        ), NumberExecutionValue(Double.NEGATIVE_INFINITY))
//
//        asCollectionEquals(mapOf(
//                "type" to "number",
//                "value" to Double.POSITIVE_INFINITY
//        ), NumberExecutionValue(Double.POSITIVE_INFINITY))
//
//        asCollectionEquals(mapOf(
//                "type" to "number",
//                "value" to Double.NaN
//        ), NumberExecutionValue(Double.NaN))
//    }
//
//
//    @Test
//    fun emptyBinary() {
//        asCollectionEquals(mapOf(
//                "type" to "binary",
//                "value" to IoUtils.base64Encode(ByteArray(0))
//        ), BinaryExecutionValue(ByteArray(0)))
//    }
//
//
//    private fun asCollectionEquals(asCollection: Map<String, Any>, executionValue: ExecutionValue) {
////        if (asCollection is ByteArray) {
////            assertTrue(asCollection contentEquals ExecutionValue.fromCollection(asCollection) as ByteArray)
////        }
////        else {
//            assertEquals(executionValue, ExecutionValue.fromJsonCollection(asCollection))
//            assertEquals(asCollection, executionValue.toJsonCollection())
////        }
//    }
//}