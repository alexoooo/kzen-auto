package tech.kzen.auto.server.codegen

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.codegen.ModuleReflectionGenerator
import java.nio.file.Paths


fun main() {
    ModuleReflectionGenerator.generate(
            Paths.get("kzen-auto-jvm/src/main/kotlin"),
            ClassName("tech.kzen.auto.client.codegen.KzenAutoJvmModule"))
}