package tech.kzen.auto.common.objects.codec

import tech.kzen.auto.common.api.ResultCodec


class NullCodec: ResultCodec {
    override fun encode(instance: Any): ByteArray {
        throw UnsupportedOperationException("Null expected: $instance")
    }

    override fun decode(bytes: ByteArray): Any {
        throw UnsupportedOperationException("Null expected: ${bytes.toList()}")
    }
}