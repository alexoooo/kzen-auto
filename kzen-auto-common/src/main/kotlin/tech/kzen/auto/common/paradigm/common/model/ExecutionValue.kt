package tech.kzen.auto.common.paradigm.common.model

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import tech.kzen.lib.platform.IoUtils


//---------------------------------------------------------------------------------------------------------------------
sealed class ExecutionValue
    : Digestible
{
    companion object {
        private const val typeKey = "type"
        private const val valueKey = "value"

        private const val nullType = "null"
        private const val textType = "text"
        private const val booleanType = "boolean"
        private const val numberType = "number"
        private const val binaryType = "binary"
        private const val listType = "list"
        private const val mapType = "map"


        fun of(value: Any?): ExecutionValue {
            return ofArbitrary(value)
                    ?: TODO("Not supported (yet): $value")
        }


        fun ofArbitrary(value: Any?): ExecutionValue? {
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
                    ListExecutionValue(value.map {
                        ofArbitrary(it) ?: return null
                    })

                is Map<*, *> ->
                    MapExecutionValue(value.entries.map {
                        val key = it.key as? String
                                ?: return null

                        val subValue = ofArbitrary(it.value)
                                ?: return null

                        key to subValue
                    }.toMap())

                else ->
                    TODO("Not supported: $value")
            }
        }


        fun fromCollection(asCollection: Map<String, Any>): ExecutionValue {
            return when (asCollection[typeKey]) {
                null ->
                    throw IllegalArgumentException("'${typeKey}' missing: $asCollection")

                nullType ->
                    NullExecutionValue

                textType ->
                    TextExecutionValue(asCollection[valueKey] as String)

                booleanType ->
                    BooleanExecutionValue(asCollection[valueKey] as Boolean)

                numberType ->
                    NumberExecutionValue(
                            asCollection[valueKey] as Double)

                binaryType ->
                    BinaryExecutionValue(
                            IoUtils.base64Decode(asCollection[valueKey] as String))

                listType ->
                    ListExecutionValue(
                            (asCollection[valueKey] as List<*>).map {
                                @Suppress("UNCHECKED_CAST")
                                fromCollection(it as Map<String, Any>)
                            }
                    )

                mapType ->
                    MapExecutionValue(
                            (asCollection[valueKey] as Map<*, *>).map{
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
                    typeKey to nullType)

            is TextExecutionValue ->
                toCollection(textType, value)

            is BooleanExecutionValue ->
                toCollection(booleanType, value)

            is NumberExecutionValue ->
                toCollection(numberType, value)

            is BinaryExecutionValue ->
                toCollection(binaryType, IoUtils.base64Encode(value))

            is ListExecutionValue ->
                toCollection(listType, values.map { it.toCollection() })

            is MapExecutionValue ->
                toCollection(mapType, values.mapValues { it.value.toCollection() })
        }
    }


    private fun toCollection(type: String, value: Any): Map<String, Any> {
        return mapOf(
                typeKey to type,
                valueKey to value)
    }


    override fun digest(): Digest {
        val digest = Digest.Builder()
        digest(digest)
        return digest.digest()
    }


    override fun digest(builder: Digest.Builder) {
        when (this) {
            NullExecutionValue ->
                builder.addInt(0)

            is TextExecutionValue -> {
                builder.addInt(1)
                builder.addUtf8(value)
            }

            is BooleanExecutionValue -> {
                builder.addInt(2)
                builder.addBoolean(value)
            }

            is NumberExecutionValue -> {
                builder.addInt(3)
                builder.addDouble(value)
            }

            is BinaryExecutionValue -> {
                builder.addInt(4)
                builder.addBytes(value)
            }

            is ListExecutionValue -> {
                builder.addInt(5)
                builder.addDigestibleList(values)
            }

            is MapExecutionValue -> {
                builder.addInt(6)
                builder.addInt(values.size)
                values.forEach {
                    builder.addUtf8(it.key)
                    it.value.digest(builder)
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
    }


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