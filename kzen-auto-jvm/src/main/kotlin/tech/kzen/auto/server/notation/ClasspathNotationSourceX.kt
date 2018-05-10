package tech.kzen.auto.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.read.flat.source.NotationSource


class ClasspathNotationSourceX : NotationSource {
    override suspend fun read(location: ProjectPath): ByteArray {
        val loader = Thread.currentThread().getContextClassLoader()
        return loader.getResource(location.relativeLocation).readBytes()
//        return loader.getResource(classpath).readBytes()
//        return javaClass.getResource(classpath).readBytes()
    }
}