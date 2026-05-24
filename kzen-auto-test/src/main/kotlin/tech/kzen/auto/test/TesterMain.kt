package tech.kzen.auto.test

import tech.kzen.auto.test.codegen.KzenAutoTestModule
import tech.kzen.auto.test.server.process.KzenAutoSubprocessRegistry


object TesterMain {
    @JvmStatic
    fun main(args: Array<String>) {
        KzenAutoTestModule.register()

        Runtime.getRuntime().addShutdownHook(Thread({
            KzenAutoSubprocessRegistry.closeAll()
        }, "kzen-auto-test-subprocess-cleanup"))

        tech.kzen.auto.server.main(args)
    }
}
