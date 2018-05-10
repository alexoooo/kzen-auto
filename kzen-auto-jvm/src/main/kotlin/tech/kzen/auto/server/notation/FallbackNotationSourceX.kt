package tech.kzen.auto.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.read.flat.source.NotationSource


class FallbackNotationSourceX(
        private val sources: List<NotationSource>
) : NotationSource {
    override suspend fun read(location: ProjectPath): ByteArray {
        for (source in sources) {
            try {
                return source.read(location)
            }
            catch (ignored: Exception) {}
        }

        throw IllegalArgumentException("Unable to read: $location")
    }
}