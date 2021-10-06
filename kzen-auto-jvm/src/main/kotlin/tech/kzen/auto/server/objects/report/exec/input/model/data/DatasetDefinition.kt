package tech.kzen.auto.server.objects.report.exec.input.model.data

import tech.kzen.auto.server.objects.plugin.model.ClassLoaderHandle


data class DatasetDefinition<T>(
    val items: List<FlatDataContentDefinition<T>>,
//    val classLoader: URLClassLoader?
    val classLoaderHandle: ClassLoaderHandle
): AutoCloseable {
    override fun close() {
//        for (flatDataContentDefinition in items) {
//            flatDataContentDefinition.processorDefinition.close()
//        }
        classLoaderHandle.close()
    }
}