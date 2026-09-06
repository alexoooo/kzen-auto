package tech.kzen.auto.server.context.runtime.kit

import tech.kzen.auto.common.objects.document.plugin.model.PluginScopeDetail
import java.nio.file.Path


/**
 * The outcome of one kit run: the boot errors (empty when the universe booted), the scope rows in the same
 * shape the Plugin document shows, the expression-identity results (verify mode), and every expectation that
 * was not met. [ok] is "no unmet expectation" — a boot error is a problem only when none was expected.
 */
class KitReport(
    val pluginRoot: Path,
    val mode: PluginCompatibilityKit.Mode,
    val bootErrors: List<String>,
    val scopes: List<PluginScopeDetail>,
    val expressionIdentity: Map<String, String>,
    val problems: List<String>
) {
    companion object {
        const val identical = "identical"
    }


    val ok: Boolean
        get() = problems.isEmpty()


    fun toMarkdown(): String {
        val out = StringBuilder()
        out.append("# Plugin compatibility (").append(mode.name.lowercase()).append("): `").append(pluginRoot).append("`\n\n")
        out.append(if (ok) "**OK**" else "**${problems.size} problem(s)**").append("\n\n")
        for (problem in problems) {
            out.append("- ").append(problem).append('\n')
        }
        if (bootErrors.isNotEmpty()) {
            out.append("\n## Boot errors\n\n")
            for (error in bootErrors) {
                out.append("- ").append(error).append('\n')
            }
        }
        for (scope in scopes) {
            out.append("\n## ").append(scope.id)
            if (scope.isApplication) out.append(" (application classpath)")
            out.append(" — ").append(if (scope.loaded) "loaded" else "FAILED").append('\n')
            scope.version?.let { out.append("- version: ").append(it).append('\n') }
            scope.spiVersion?.let { out.append("- spi: ").append(it).append('\n') }
            scope.directory?.let { out.append("- directory: `").append(it).append("`\n") }
            if (scope.jars.isNotEmpty()) out.append("- jars: ").append(scope.jars.joinToString()).append('\n')
            scope.failure?.let { out.append("- failure: ").append(it).append('\n') }
            for (reader in scope.readers) out.append("- reader: ").append(reader).append('\n')
            for (document in scope.documents) out.append("- document: ").append(document.path).append(" ← `").append(document.origin).append("`\n")
            for (module in scope.generatedModules) out.append("- generated module: ").append(module).append('\n')
            for (klass in scope.classes) {
                out.append("- class: ").append(klass.className).append(" — ").append(klass.availability)
                klass.detail?.let { out.append(" (").append(it).append(")") }
                out.append('\n')
            }
            for (name in scope.shadowedClasses) out.append("- shadowed by application: ").append(name).append('\n')
            for (name in scope.ambiguousClasses) out.append("- ambiguous: ").append(name).append('\n')
            for (failure in scope.failures) out.append("- failure: ").append(failure).append('\n')
        }
        if (expressionIdentity.isNotEmpty()) {
            out.append("\n## Expression identity\n\n")
            for ((className, result) in expressionIdentity) {
                out.append("- ").append(className).append(": ").append(result).append('\n')
            }
        }
        return out.toString()
    }
}
