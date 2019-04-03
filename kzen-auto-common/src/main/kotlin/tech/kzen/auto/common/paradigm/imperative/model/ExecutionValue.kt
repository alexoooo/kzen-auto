package tech.kzen.auto.common.paradigm.imperative.model

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.IoUtils


//---------------------------------------------------------------------------------------------------------------------
// TODO: use {type: <enum>[, value: <nested>]}
sealed class ExecutionValue {
    companion object {
        fun of(value: Any?): ExecutionValue {
            return when (value) {
                null ->
                    NullExecutionValue

                is String ->
                    TextExecutionValue(value)

                is Boolean ->
                    BooleanExecutionValue(value)

                is Number ->
                    NumberExecutionValue(value.toDouble())

                is ByteArray ->
                    BinaryExecutionValue(value)

                is List<*> ->
                    ListExecutionValue(value.map { of(it) })

                is Map<*, *> ->
                    MapExecutionValue(value.entries.map {
                        it.key as String to of(it.value)
                    }.toMap())

                else ->
                    TODO("Not supported (yet): $value")
            }
        }


        fun fromCollection(asCollection: Map<String, Any>): ExecutionValue {
            return when (asCollection["type"]) {
                null ->
                    throw IllegalArgumentException("'type' missing: $asCollection")

                "null" ->
                    NullExecutionValue

                "text" ->
                    TextExecutionValue(asCollection["value"] as String)

                "boolean" ->
                    BooleanExecutionValue(asCollection["value"] as Boolean)

                "number" ->
                    NumberExecutionValue(
                            asCollection["value"] as Double)

                "binary" ->
                    BinaryExecutionValue(
                            IoUtils.base64Decode(asCollection["value"] as String))

                "list" ->
                    ListExecutionValue(
                            (asCollection["value"] as List<*>).map {
                                @Suppress("UNCHECKED_CAST")
                                fromCollection(it as Map<String, Any>)
                            }
                    )

                "map" ->
                    MapExecutionValue(
                            (asCollection["value"] as Map<*, *>).map{
                                @Suppress("UNCHECKED_CAST")
                                it.key as String to fromCollection(it.value as Map<String, Any>)
                            }.toMap()
                    )

                else ->
                    TODO("Not supported (yet): $asCollection")
            }
        }
    }


    fun get(): Any? {
        return when (this) {
            NullExecutionValue ->
                null

            is TextExecutionValue ->
                value

            is BooleanExecutionValue ->
                value

            is NumberExecutionValue ->
                value

            is BinaryExecutionValue ->
                value

            is ListExecutionValue ->
                values.map { it.get() }

            is MapExecutionValue ->
                values.mapValues { it.value.get() }
        }
    }


    fun toCollection(): Map<String, Any> {
        return when (this) {
            NullExecutionValue -> mapOf(
                    "type" to "null")

            is TextExecutionValue ->
                toCollection("text", value)

            is BooleanExecutionValue ->
                toCollection("boolean", value)

            is NumberExecutionValue ->
                toCollection("number", value)

            is BinaryExecutionValue ->
                toCollection("binary", IoUtils.base64Encode(value))

            is ListExecutionValue ->
                toCollection("list", values.map { it.toCollection() })

            is MapExecutionValue ->
                toCollection("map", values.mapValues { it.value.toCollection() })
        }
    }


    private fun toCollection(type: String, value: Any): Map<String, Any> {
        return mapOf(
                "type" to type,
                "value" to value)
    }


    fun digest(): Digest {
        val digest = Digest.Streaming()
        digest(digest)
        return digest.digest()
    }


    fun digest(digest: Digest.Streaming) {
        when (this) {
            NullExecutionValue ->
                digest.addMissing()

            is TextExecutionValue ->
                digest.addUtf8(value)

            is BooleanExecutionValue ->
                digest.addBoolean(value)

            is NumberExecutionValue -> {
                digest.addDouble(value)
            }

            is BinaryExecutionValue ->
                digest.addBytes(value)

            is ListExecutionValue -> {
                digest.addInt(values.size)
                values.forEach { it.digest(digest) }
            }

            is MapExecutionValue -> {
                digest.addInt(values.size)
                values.forEach {
                    digest.addUtf8(it.key)
                    it.value.digest(digest)
                }
            }
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
object NullExecutionValue: ExecutionValue() {
    override fun toString(): String {
        return "Null"
    }
}


//---------------------------------------------------------------------------------------------------------------------
sealed class ScalarExecutionValue: ExecutionValue()


data class TextExecutionValue(
        val value: String
): ScalarExecutionValue()


data class BooleanExecutionValue(
        val value: Boolean
): ScalarExecutionValue()


data class NumberExecutionValue(
        val value: Double
): ScalarExecutionValue()


data class BinaryExecutionValue(
        val value: ByteArray
): ScalarExecutionValue() {
    private val cache: MutableMap<String, Any> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    fun <T> cache(key: String, valueProvider: () -> T): T {
        val existing = cache[key]
        if (existing != null) {
            return existing as T
        }

        val added = valueProvider()
        cache[key] = added as Any

        return added
    }


    fun asBase64(): String {
        return cache("base64") { IoUtils.base64Encode(value) }

//        if (base64 == null) {
//            base64 = IoUtils.base64Encode(value)
//        }
//        return base64!!
    }


//    override fun get(): ByteArray {
//        return value
//    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BinaryExecutionValue

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}


//---------------------------------------------------------------------------------------------------------------------
sealed class StructuredExecutionValue: ExecutionValue()


data class ListExecutionValue(
        val values: List<ExecutionValue>
): StructuredExecutionValue()


data class MapExecutionValue(
        val values: Map<String, ExecutionValue>
): StructuredExecutionValue()