package tech.kzen.auto.common.util.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tech.kzen.auto.platform.Url
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Serializable(with = DataLocationSerializer::class)
data class DataLocation(
    val filePath: FilePath?,
    val url: Url?
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        @Suppress("ConstPropertyName")
        private const val unknownLocation = "unknown"
        val unknown = DataLocation(null, null)


        fun of(location: String): DataLocation {
            return parse(location)
                ?: throw IllegalArgumentException("Invalid: $location")
        }


        fun parse(location: String): DataLocation? {
            if (location == unknownLocation) {
                return unknown
            }

            val filePath = FilePath.parse(location)
            if (filePath != null) {
                return ofFile(filePath)
            }

            val url = Url.parse(location)
            if (url != null) {
                return ofUrl(url)
            }

            return null
        }


        fun ofFile(filePath: FilePath): DataLocation {
            return DataLocation(filePath, null)
        }


        fun ofUrl(url: Url): DataLocation {
            return DataLocation(null, url)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var digestCache: Digest? = null


    //-----------------------------------------------------------------------------------------------------------------
    init {
        require(filePath == null || url == null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Segment arithmetic is the held value object's own, like parent(): only it knows its separators and its
    // roots. An unknown location has neither, and names itself.
    fun fileName(): String {
        filePath?.let { return it.fileName() }
        url?.let { return it.fileName() }
        return unknownLocation
    }


    fun innerExtension(): String {
        val fileName = fileName()

        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val outerExtension = outerExtension(fileName)

        return when (outerExtension) {
            "gz" -> {
                val withoutOuterExtension = fileName.substring(0, fileName.length - outerExtension.length - 1)
                outerExtension(withoutOuterExtension)
            }

            else ->
                outerExtension
        }
    }


    private fun outerExtension(fileName: String): String {
        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val dotIndex = fileName.lastIndexOf('.')

        return when (dotIndex) {
            -1 -> ""
            else -> fileName.substring(dotIndex + 1)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Path arithmetic is the held value object's own: only it knows its separators, its roots and its opaque
    // forms. An unknown location has neither, and so no parent.
    fun parent(): DataLocation? {
        if (filePath != null) {
            return filePath.parent()?.let { ofFile(it) }
        }

        if (url != null) {
            return url.parent()?.let { ofUrl(it) }
        }

        return null
    }


    fun ancestors(): List<DataLocation> {
        val builder = mutableListOf<DataLocation>()
        var parent = this
        while (true) {
            builder.add(parent)
            val nextParent = parent.parent()
                ?: break
            parent = nextParent
        }
        builder.reverse()
        return builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return filePath?.location
            ?: url?.toString()
            ?: unknownLocation
    }


    override fun digest(): Digest {
        val existing = digestCache
        if (existing != null) {
            return existing
        }

        val builder = Digest.Builder()
        builder.addDigestibleNullable(filePath)
        builder.addDigestibleNullable(url)
        val computed = builder.digest()

        digestCache = computed
        return computed
    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigest(digest())
    }


    override fun toString(): String {
        return asString()
    }
}


//---------------------------------------------------------------------------------------------------------------------
// SER3: first kzen-auto-common value-object serializer; same pattern as SER2's kzen-lib ones (cf. DocumentPath,
// LogicRunId) — delegate to the existing asString()/of() round-trip so the wire form is the value object's canonical
// string. Bound via @Serializable(with) so DTOs referencing DataLocation need no per-field annotation.
// Hand-written by convention, and because the wire form must be the value object's canonical string rather than
// its two-field structure. @Serializable(with) also suppresses field analysis, so neither the private digestCache
// nor the FilePath/Url pair needs a serializer of its own.
object DataLocationSerializer: KSerializer<DataLocation> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.kzen.auto.common.util.data.DataLocation", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DataLocation) {
        encoder.encodeString(value.asString())
    }

    override fun deserialize(decoder: Decoder): DataLocation {
        return DataLocation.of(decoder.decodeString())
    }
}
