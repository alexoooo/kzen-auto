package tech.kzen.auto.platform
//
//
//object DataLocationExtensions {
//    //-----------------------------------------------------------------------------------------------------------------
//    private const val fileScheme = "file://"
//    private const val networkHost = "//"
//    private const val localHost = "/"
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    fun DataLocation.Companion.tryParse(simpleValue: String): DataLocation? {
//        val trimmed = simpleValue.trim()
//
//        val withoutQuotes =
//            if (trimmed.startsWith('"')) {
//                if (! trimmed.endsWith('"')) {
//                    return null
//                }
//
//                trimmed.substring(1, trimmed.length - 1)
//            }
//            else {
//                trimmed
//            }
//
//
//        return null
//    }
//
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    fun DataLocation.toSimpleString(): String {
//        val uri = asUri()
//
//        if (! uri.startsWith(fileScheme)) {
//            return uri
//        }
//
//        val afterScheme = uri.substring(fileScheme.length)
//
//        val afterSchemeContent = when {
//            afterScheme.startsWith(networkHost) ->
//                afterScheme
//
//            afterScheme.startsWith(localHost) ->
//                afterScheme.substring(localHost.length)
//
//            else -> uri
//        }
//
//        return UriUtils.decodeUriComponent(afterSchemeContent)
//    }
//
//
//    fun DataLocation.simpleParent(): String {
//        val simpleString = toSimpleString()
//
//        @Suppress("MoveVariableDeclarationIntoWhen")
//        val lastSeparator = simpleString.lastIndexOf('/')
//
//        return when (lastSeparator) {
//            -1 -> simpleString
//            else -> simpleString.substring(0, lastSeparator + 1)
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    fun DataLocation.fileName(): String {
//        val asString = toSimpleString()
//
//        @Suppress("MoveVariableDeclarationIntoWhen")
//        val lastSeparator = asString.lastIndexOf('/')
//
//        return when (lastSeparator) {
//            -1 -> asString
//            else -> asString.substring(lastSeparator + 1)
//        }
//    }
//
//
//    fun DataLocation.innerExtension(): String {
//        val fileName = fileName()
//
//        @Suppress("MoveVariableDeclarationIntoWhen")
//        val outerExtension = outerExtension(fileName)
//
//        return when (outerExtension) {
//            "gz" -> {
//                val withoutOuterExtension = fileName.substring(0, fileName.length - outerExtension.length - 1)
//                outerExtension(withoutOuterExtension)
//            }
//
//            else ->
//                outerExtension
//        }
//    }
//
//
//    private fun outerExtension(fileName: String): String {
//        @Suppress("MoveVariableDeclarationIntoWhen")
//        val dotIndex = fileName.lastIndexOf('.')
//
//        return when (dotIndex) {
//            -1 -> ""
//            else -> fileName.substring(dotIndex + 1)
//        }
//    }
//}