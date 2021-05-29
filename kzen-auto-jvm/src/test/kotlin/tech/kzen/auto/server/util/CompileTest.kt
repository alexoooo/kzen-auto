package tech.kzen.auto.server.util

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector.Companion.NONE
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys.MODULE_NAME
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.junit.Ignore
import org.junit.Test


class CompileTest {
    @Test
    @Ignore
    fun foo() {
        val configuration = CompilerConfiguration()
        configuration.put(MODULE_NAME, "test")
        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, NONE)

        for (i in 0 .. 1_000_000_000) {
            println("i = $i")
            val parentDisposable = Disposer.newDisposable()
            KotlinCoreEnvironment.createForProduction(
                parentDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
            )
            Disposer.dispose(parentDisposable);
            System.gc();
        }
    }
}