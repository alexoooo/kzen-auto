package tech.kzen.auto.platform
//
//import tech.kzen.lib.common.util.Digest
//import tech.kzen.lib.common.util.Digestible
//import java.net.URI
//import java.nio.file.Path
//
//
//actual class DataLocation(
//    val uri: URI
//): Digestible {
//    //-----------------------------------------------------------------------------------------------------------------
//    actual companion object {
//        val literal = DataLocation(URI.create("literal"))
//
//        actual fun ofUri(uri: String): DataLocation {
//            return DataLocation(URI.create(uri))
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
//        return uri.toString()
//    }
//
//
//    fun toPath(): Path? {
//        return try {
//            Path.of(uri)
//        }
//        catch (e: Exception) {
//            null
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun toString(): String {
//        return asUri()
//    }
//
//    override fun digest(builder: Digest.Builder) {
//        builder.addUtf8(asUri())
//    }
//
//
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
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