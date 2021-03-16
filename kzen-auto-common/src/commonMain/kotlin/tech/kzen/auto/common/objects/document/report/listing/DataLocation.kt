package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.auto.platform.Url
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class DataLocation(
    val filePath: FilePath?,
    val url: Url?
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
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
    fun fileName(): String {
        val asString = asString()

        @Suppress("MoveVariableDeclarationIntoWhen")
        val lastSeparator = asString.lastIndexOf('/')

        return when (lastSeparator) {
            -1 -> asString
            else -> asString.substring(lastSeparator + 1)
        }
    }


    fun innerExtension(): String {
        val fileName = fileName()

        @Suppress("MoveVariableDeclarationIntoWhen")
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
        @Suppress("MoveVariableDeclarationIntoWhen")
        val dotIndex = fileName.lastIndexOf('.')

        return when (dotIndex) {
            -1 -> ""
            else -> fileName.substring(dotIndex + 1)
        }
    }


    fun parent(): DataLocation {
        val simpleString = toString()

        @Suppress("MoveVariableDeclarationIntoWhen")
        val lastSeparator = simpleString.lastIndexOf('/')

        return when (lastSeparator) {
            -1 -> of(simpleString)
            else -> of(simpleString.substring(0, lastSeparator + 1))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return filePath?.location
            ?: url?.toString()
            ?: unknownLocation
    }


    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleNullable(filePath)
        builder.addDigestibleNullable(url)
    }
}
