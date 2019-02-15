package tech.kzen.auto.common.exec

import tech.kzen.lib.common.util.Digest


//---------------------------------------------------------------------------------------------------------------------
// TODO: use {type: <enum>[, value: <nested>]}
sealed class ExecutionValue {
    companion object {
        fun fromCollection(asCollection: Any?): ExecutionValue {
            return when (asCollection) {
                null ->
                    NullExecutionValue

                is String ->
                    TextExecutionValue(asCollection)

                is Boolean ->
                    BooleanExecutionValue(asCollection)

                is Number ->
                    NumberExecutionValue(asCollection.toDouble())

                is ByteArray ->
                    BinaryExecutionValue(asCollection)

                is List<*> ->
                    ListExecutionValue(asCollection.map { fromCollection(it) })

                is Map<*, *> ->
                    MapExecutionValue(asCollection.entries.map {
                        it.key as String to fromCollection(it.value)
                    }.toMap())

                else ->
                    TODO("Not supported (yet): $asCollection")
            }
        }
    }


    fun toCollection(): Any? {
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
                values.map { it.toCollection() }

            is MapExecutionValue ->
                values.mapValues { it.value.toCollection() }
        }
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

            is ListExecutionValue ->
                values.map { it.toCollection() }

            is MapExecutionValue ->
                values.mapValues { it.value.toCollection() }
        }
    }
}


object NullExecutionValue: ExecutionValue()


//---------------------------------------------------------------------------------------------------------------------
sealed class ScalarExecutionValue: ExecutionValue() {
    abstract fun get(): Any
}


data class TextExecutionValue(
        val value: String
): ScalarExecutionValue() {
    override fun get(): String {
        return value
    }
}


data class BooleanExecutionValue(
        val value: Boolean
): ScalarExecutionValue() {
    override fun get(): Any {
        return value
    }
}


data class NumberExecutionValue(
        val value: Double
): ScalarExecutionValue() {
    override fun get(): Double {
        return value
    }
}


data class BinaryExecutionValue(
        val value: ByteArray
): ScalarExecutionValue() {
    override fun get(): ByteArray {
        return value
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