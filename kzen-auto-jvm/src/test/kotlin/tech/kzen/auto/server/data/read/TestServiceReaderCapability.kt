package tech.kzen.auto.server.data.read

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.read.ContentCapabilityIdentity
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ReaderConfig
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.auto.plugin.api.data.ReaderCapability
import tech.kzen.auto.plugin.api.data.ReaderInspectionRequest
import tech.kzen.auto.plugin.api.data.ReaderOpenRequest
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.LiteralDataValues


class TestServiceReaderCapability: ReaderCapability {
    companion object {
        val serviceIdentity = ReaderCapabilityIdentity("third.party.test", "text-reader", "1")
    }

    data class Config(val prefix: String): ReaderConfig

    override val identity: ReaderCapabilityIdentity = serviceIdentity

    override fun decode(config: ExecutionValue): ReaderConfig {
        val map = config as? MapExecutionValue
            ?: throw IllegalArgumentException("Plugin reader config must be a map")
        return Config((map.values["prefix"] as? TextExecutionValue)?.value
            ?: throw IllegalArgumentException("Plugin reader prefix must be text"))
    }

    override fun validate(config: ReaderConfig) {
        require(config is Config) { "Plugin reader config expected" }
    }

    override fun canonicalize(config: ReaderConfig): ReaderConfig {
        validate(config)
        return Config((config as Config).prefix)
    }

    override fun encode(config: ReaderConfig): ExecutionValue {
        val canonical = canonicalize(config) as Config
        return MapExecutionValue(mapOf("prefix" to TextExecutionValue(canonical.prefix)))
    }

    override fun requiredContent(config: ReaderConfig): ContentCapabilityIdentity {
        validate(config)
        return ContentCapabilityIdentity.sequentialBytes
    }

    override suspend fun open(request: ReaderOpenRequest): DataCursor {
        val config = canonicalize(request.config) as Config
        val shapeValue = LiteralDataValues.lift("")
        val shape = DataShape(shapeValue.contract, ShapeProvenance.Declared, ShapeStability.Stable)
        return object: DataCursor {
            override val shape: DataShape = shape
            private var value: DataValue? = null
            private var finished = false
            private var closed = false

            override fun hasNext(): Boolean {
                check(!closed) { "Plugin cursor is closed" }
                if (value != null) return true
                if (finished) return false
                val output = ArrayList<Byte>()
                val buffer = ByteArray(16)
                while (true) {
                    val count = request.bytes.read(buffer)
                    if (count == -1) break
                    for (index in 0 until count) output += buffer[index]
                }
                value = LiteralDataValues.lift(
                    config.prefix + output.toByteArray().decodeToString())
                finished = true
                return true
            }

            override fun next(): DataValue {
                if (!hasNext()) throw NoSuchElementException()
                return requireNotNull(value).also { value = null }
            }

            override fun close() {
                closed = true
                value = null
            }
        }
    }

    override suspend fun inspect(request: ReaderInspectionRequest): DataShape {
        return open(request.open).use { cursor ->
            if (request.maximumRecords > 0 && cursor.hasNext()) cursor.next()
            cursor.shape
        }
    }
}
