package tech.kzen.auto.server.context.runtime

import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path


/**
 * Not a test of anything: exports the demo universe the boot suite uses (a healthy scope with a reader, a
 * generated module, a service-needing class and a bundled document; two scopes defining one name; a scope
 * shadowing an application class; a corrupt scope) into the directory named by the `KZEN_UNIVERSE_EXPORT`
 * environment variable, for a manual browser check of the Plugin document over `--plugin.root=`. A no-op
 * when the variable is absent, which is every ordinary run.
 *
 * ```
 * KZEN_UNIVERSE_EXPORT=/tmp/universe ./gradlew :kzen-auto-jvm:test --tests "*PluginUniverseExportTest"
 * ```
 */
class PluginUniverseExportTest {
    @Test
    fun exportDemoUniverseWhenRequested() {
        val target = System.getenv("KZEN_UNIVERSE_EXPORT")?.takeIf { it.isNotBlank() }
            ?: return
        val root = Path.of(target)
        Files.createDirectories(root)
        exportDemoUniverse(root)
    }


    private fun exportDemoUniverse(root: Path) {
        val universe = PluginUniverseBuilder(root)
        universe.plugin("alpha") {
            jar("alpha.jar") {
                manifest("id: alpha\nversion: 0.1-demo\nspi: 1\n")
                javaClass("fixture.alpha.AlphaReader",
                    PluginFixtures.readerCapability("fixture.alpha", "AlphaReader", "fixture.alpha", "alpha-reader"))
                javaClass("fixture.alpha.AlphaGen", PluginFixtures.reflectClass("fixture.alpha", "AlphaGen"))
                javaClass("fixture.alpha.AlphaModule",
                    PluginFixtures.moduleReflection("fixture.alpha", "AlphaModule", "AlphaGen"))
                javaClass("fixture.alpha.Repo", "package fixture.alpha;\npublic interface Repo {}")
                javaClass("fixture.alpha.NeedsRepo", """
                    package fixture.alpha;
                    @tech.kzen.lib.common.reflect.Reflect
                    public class NeedsRepo {
                        public NeedsRepo(@tech.kzen.lib.common.reflect.Service Repo repo) {}
                    }
                """.trimIndent())
                resource(PluginFixtures.servicesEntry(), "fixture.alpha.AlphaReader\n")
                resource(PluginFixtures.moduleReflectionServicesEntry(), "fixture.alpha.AlphaModule\n")
                resource("notation/auto-jvm/alpha/alpha-doc.yaml", "AlphaDoc:\n  is: ScriptStep\n  class: fixture.alpha.AlphaGen\n  label: \"\"\n")
            }
        }
        for (name in listOf("dup-one", "dup-two")) {
            universe.plugin(name) {
                jar("$name.jar") { javaClass("fixture.dup.Same", "package fixture.dup;\npublic class Same {}") }
            }
        }
        val shadowedClass = PluginScopeId::class.java
        val shadowBytes = shadowedClass.getResourceAsStream(shadowedClass.simpleName + ".class")!!.readAllBytes()
        universe.plugin("shadow") {
            jar("shadow.jar") { bytes(shadowedClass.name.replace('.', '/') + ".class", shadowBytes) }
        }
        universe.plugin("broken") { jar("broken.jar") { corrupt() } }
    }
}
