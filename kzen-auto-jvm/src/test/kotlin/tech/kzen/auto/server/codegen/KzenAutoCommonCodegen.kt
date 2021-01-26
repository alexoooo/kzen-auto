package tech.kzen.auto.server.codegen

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.codegen.ModuleReflectionGenerator
import java.nio.file.Paths


object KzenAutoCommonCodegen {
    val commonSourceDir = Paths.get("kzen-auto-common/src/commonMain/kotlin")

    @JvmStatic
    fun main(args: Array<String>) {
        ModuleReflectionGenerator.generate(
            commonSourceDir,
            ClassName("tech.kzen.auto.common.codegen.KzenAutoCommonModule"))
    }
}