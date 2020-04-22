package tech.kzen.auto.server.codegen

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.codegen.ModuleReflectionGenerator
import java.nio.file.Paths


fun main() {
    ModuleReflectionGenerator.generate(
            Paths.get("kzen-auto-common/src/commonMain/kotlin"),
            ClassName("tech.kzen.auto.common.codegen.KzenAutoCommonModule"))
}