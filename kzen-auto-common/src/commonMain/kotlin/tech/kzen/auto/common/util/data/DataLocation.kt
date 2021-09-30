package tech.kzen.auto.common.util.data

import tech.kzen.auto.platform.Url
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class DataLocation(
    val filePath: FilePath?,
    val url: Url?
):
    Digestible
{
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
    private var digestCache: Digest? = null


    //-----------------------------------------------------------------------------------------------------------------
    init {
        require(filePath == null || url == null)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun fileName(): String {
        if (filePath != null) {
            if (filePath.isWindowsDriveRoot()) {
                return filePath.location.substring(0, 2)
            }
            else if (filePath.isWindowsNetworkShare()) {
                return filePath.location.substring(filePath.location.lastIndexOf('\\') + 1)
            }
        }

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


    //-----------------------------------------------------------------------------------------------------------------
    fun parent(): DataLocation? {
        if (filePath != null) {
            if (filePath.isRoot()) {
                return null
            }
        }
        else if (url != null) {
            if (url.path.isEmpty()) {
                return null
            }
        }
        else {
            return null
        }

        val simpleString = asString()

        val withoutTrainingSlash =
            if (simpleString.endsWith("/")) {
                simpleString.substring(0, simpleString.length - 1)
            }
            else {
                simpleString
            }

        @Suppress("MoveVariableDeclarationIntoWhen")
        val lastSeparator = withoutTrainingSlash.lastIndexOf('/')

        return when {
            lastSeparator == -1 ->
                if (filePath != null &&
                        filePath.type == FilePathType.NetworkWindows) {
                    val backslashIndex = withoutTrainingSlash.lastIndexOf('\\')
                    if (backslashIndex <= 1) {
                        null
                    }
                    else {
                        of(withoutTrainingSlash.substring(0, backslashIndex))
                    }
                }
                else {
                    null
                }

            lastSeparator == 0 ->
                of("/")

            filePath != null &&
                    filePath.type == FilePathType.AbsoluteWindows &&
                    lastSeparator == 2 ->
                of(simpleString.substring(0, lastSeparator + 1))

            else -> of(simpleString.substring(0, lastSeparator))
        }
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
