package tech.kzen.auto.platform

//import tech.kzen.lib.common.util.Digest
//import tech.kzen.lib.common.util.Digestible
//
//
//actual class DataLocation(
//    private val uri: String
//): Digestible {
//    //-----------------------------------------------------------------------------------------------------------------
//    actual companion object {
//        actual fun ofUri(uri: String): DataLocation {
//            return DataLocation(uri)
//        }
//
//        actual fun ofUriOrNull(uri: String): DataLocation? {
//            TODO("Not yet implemented")
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    actual fun asUri(): String {
//        return uri
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun toString(): String {
//        return uri
//    }
//
//
//    override fun digest(builder: Digest.Builder) {
//        builder.addUtf8(uri)
//    }
//
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (other == null || this::class.js != other::class.js) return false
//
//        other as DataLocation
//
//        if (uri != other.uri) return false
//
//        return true
//    }
//
//
//    override fun hashCode(): Int {
//        return uri.hashCode()
//    }
//}