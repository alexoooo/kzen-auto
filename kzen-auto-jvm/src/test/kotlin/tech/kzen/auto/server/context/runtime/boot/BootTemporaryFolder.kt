package tech.kzen.auto.server.context.runtime.boot

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.junit.rules.TemporaryFolder
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime


/**
 * A boot test's temporary folder, deleted or failing the test like any other, except that it first releases what
 * holds the pinned universe's jars open for the JVM's life, which on Windows would block the delete: the scope
 * loaders, and the standing compiler environment's memory-mapped handles on every jar an expression compiled
 * against. Safe because each boot test class has a JVM of its own, which ends with the test.
 */
class BootTemporaryFolder: TemporaryFolder(builder().assureDeletion()) {
    // K1-tagged because it predates the K2 frontend; the jar file system serves both
    @OptIn(K1Deprecation::class)
    override fun after() {
        if (KzenAutoRuntime.isInitialized()) {
            KzenAutoRuntime.current().scopes.closeFolderLoaders()
        }
        KotlinCoreEnvironment.applicationEnvironment?.fastJarFileSystem?.cleanOpenFilesCache()
        super.after()
    }
}
