package tech.kzen.auto.server.codegen

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.codegen.ModuleReflectionGenerator
import java.nio.file.Paths


object KzenAutoCommonCodegen {
    @JvmStatic
    fun main(args: Array<String>) {
        ModuleReflectionGenerator.generate(
                Paths.get("kzen-auto-common/src/commonMain/kotlin"),
                ClassName("tech.kzen.auto.common.codegen.KzenAutoCommonModule"))
    }
}