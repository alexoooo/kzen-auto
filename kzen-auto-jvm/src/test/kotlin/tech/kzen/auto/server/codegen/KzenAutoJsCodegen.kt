package tech.kzen.auto.server.codegen

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.codegen.ModuleReflectionGenerator
import java.nio.file.Paths


object KzenAutoJsCodegen {
    @JvmStatic
    fun main(args: Array<String>) {
        ModuleReflectionGenerator.generate(
            Paths.get("kzen-auto-js/src/jsMain/kotlin"),
            ClassName("tech.kzen.auto.client.codegen.KzenAutoJsModule"),
            KzenAutoCommonCodegen.commonSourceDir)
    }
}