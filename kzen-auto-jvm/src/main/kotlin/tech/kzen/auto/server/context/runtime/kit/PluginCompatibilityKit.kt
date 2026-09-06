package tech.kzen.auto.server.context.runtime.kit

import tech.kzen.auto.common.objects.document.plugin.model.PluginClassDetail
import tech.kzen.auto.common.objects.document.plugin.model.PluginScopeDetail
import tech.kzen.auto.server.context.KzenAutoConfig
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.context.runtime.KzenAutoRuntime
import tech.kzen.auto.server.context.runtime.KzenAutoRuntimeConfig
import tech.kzen.auto.server.context.runtime.PluginBootException
import tech.kzen.auto.server.context.runtime.PluginContributionDiscovery
import tech.kzen.auto.server.context.runtime.PluginScopeDiscovery
import tech.kzen.auto.server.context.runtime.PluginScopeId
import tech.kzen.auto.server.context.runtime.PluginScopes
import tech.kzen.auto.server.context.runtime.ScopeContributions
import tech.kzen.auto.server.objects.plugin.PluginUniverseView
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.reflect.AggregateClassLoader
import tech.kzen.lib.server.reflect.ReflectiveClassMirror
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess


/**
 * The reusable compatibility check a plugin author runs against a plugin root directory (one subdirectory per
 * plugin, as `--plugin.root=` would see it), with [KitExpectations] stating what must be found.
 *
 * - [inspect] is pure: discovery, contributions, notation, reflection and service needs, duplicates, shadowing
 *   and ambiguity, all without pinning the process runtime — so a boot-error universe can be checked in the
 *   same JVM as a healthy one. Class availability is reported as *resolved* with its service needs, since
 *   whether a workspace provides them is a property of a context.
 * - [verify] pins the process-global runtime on the directory (**one universe per JVM**: run it in its own
 *   process, as the `pluginUniverseTest` task does), creates a standalone context, resolves every expected class
 *   through the real mirror and availability view, and proves expression identity: an expression naming the
 *   class compiles against the plugin classpath and resolves to the very `Class` the aggregate loader serves.
 *
 * The scope rows are the same [PluginScopeDetail] the Plugin document shows, so what the kit prints is what a
 * user would see. Lives in the JVM implementation module deliberately: nothing here belongs on the SPI's
 * dependency graph.
 */
object PluginCompatibilityKit {
    enum class Mode { INSPECT, VERIFY }

    private const val verifyFlag = "--verify"
    private const val kitModuleName = "kzen-auto-js"

    private const val expectScopeFlag = "--expect-scope="
    private const val expectFailedScopeFlag = "--expect-failed-scope="
    private const val expectBootErrorFlag = "--expect-boot-error="
    private const val expectReaderFlag = "--expect-reader="
    private const val expectDocumentFlag = "--expect-document="
    private const val expectClassFlag = "--expect-class="
    private const val expectUnavailableClassFlag = "--expect-unavailable-class="
    private const val expectAmbiguousClassFlag = "--expect-ambiguous-class="
    private const val expectShadowedClassFlag = "--expect-shadowed-class="
    private const val expectExpressionFlag = "--expect-expression="

    private const val usage = "usage: <pluginRoot> [$verifyFlag] [$expectScopeFlag<id>] [$expectFailedScopeFlag<id>] " +
        "[$expectBootErrorFlag<substring>] [$expectReaderFlag<namespace.name@compatibility>] " +
        "[$expectDocumentFlag<path>] [$expectClassFlag<fqcn>] [$expectUnavailableClassFlag<fqcn>] " +
        "[$expectAmbiguousClassFlag<fqcn>] [$expectShadowedClassFlag<fqcn>] [$expectExpressionFlag<fqcn>] " +
        "(each --expect-* flag repeatable)"


    /**
     * `<pluginRoot> [--verify] [--expect-*=…]`: prints the Markdown report; exit code 1 when an expectation is
     * unmet, 2 for bad arguments. The `--expect-*` flags are [KitExpectations] for a shell or a foreign build's
     * test (a Maven integration test of a plugin, say) — the same checks as the Kotlin API, from a separate JVM.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val root = args.firstOrNull { !it.startsWith("--") }
        if (root == null) {
            System.err.println(usage)
            exitProcess(2)
        }
        val expectations = try {
            expectations(args)
        }
        catch (e: IllegalArgumentException) {
            System.err.println(e.message)
            System.err.println(usage)
            exitProcess(2)
        }
        val report =
            if (verifyFlag in args) verify(Path.of(root), expectations)
            else inspect(Path.of(root), expectations)
        println(report.toMarkdown())
        if (!report.ok) {
            exitProcess(1)
        }
    }


    /** The `--expect-*` flags as [KitExpectations]; an unknown `--` flag is an [IllegalArgumentException]. */
    fun expectations(args: Array<String>): KitExpectations {
        fun values(flag: String): Set<String> = args
            .filter { it.startsWith(flag) }
            .map { it.removePrefix(flag) }
            .onEach { require(it.isNotBlank()) { "$flag needs a value" } }
            .toSet()
        val known = listOf(
            expectScopeFlag, expectFailedScopeFlag, expectBootErrorFlag, expectReaderFlag, expectDocumentFlag,
            expectClassFlag, expectUnavailableClassFlag, expectAmbiguousClassFlag, expectShadowedClassFlag,
            expectExpressionFlag)
        for (arg in args) {
            if (arg.startsWith("--") && arg != verifyFlag && known.none { arg.startsWith(it) }) {
                throw IllegalArgumentException("Unknown flag: $arg")
            }
        }
        return KitExpectations(
            loadedScopes = values(expectScopeFlag),
            failedScopes = values(expectFailedScopeFlag),
            bootErrors = values(expectBootErrorFlag).toList(),
            readers = values(expectReaderFlag),
            documents = values(expectDocumentFlag),
            availableClasses = values(expectClassFlag),
            unavailableClasses = values(expectUnavailableClassFlag),
            ambiguousClasses = values(expectAmbiguousClassFlag),
            shadowedClasses = values(expectShadowedClassFlag),
            expressionClasses = values(expectExpressionFlag))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun inspect(pluginRoot: Path, expectations: KitExpectations = KitExpectations()): KitReport {
        val root = pluginRoot.toAbsolutePath().normalize()
        val applicationLoader = ClassLoaderUtils.applicationClassLoader()

        val scopes: PluginScopes
        val contributions: List<ScopeContributions>
        try {
            scopes = PluginScopeDiscovery.discover(root, applicationLoader)
            contributions = PluginContributionDiscovery.discover(scopes)
        }
        catch (e: PluginBootException) {
            return report(root, Mode.INSPECT, e.errors, listOf(), mapOf(), expectations)
        }

        val aggregate = AggregateClassLoader(
            applicationLoader,
            scopes.loadedFolders.map { AggregateClassLoader.Scope(it.id.value, it.requireClassLoader() as URLClassLoader) })
        val mirror = ReflectiveClassMirror(aggregate)
        val generated = contributions.mapNotNull { it.generatedRegistry }

        val classNames = (expectations.availableClasses + expectations.unavailableClasses).map { ClassName(it) }
        val classesByScope = mutableMapOf<PluginScopeId, MutableList<PluginClassDetail>>()
        for (className in classNames) {
            val detail = inspectClass(className, mirror, generated, aggregate)
            val definers = definingScopes(className, aggregate, scopes, contributions)
            for (scopeId in definers.ifEmpty { listOf(scopes.application.id) }) {
                classesByScope.getOrPut(scopeId) { mutableListOf() }.add(detail)
            }
        }

        val shadowed = mutableMapOf<PluginScopeId, MutableList<String>>()
        val ambiguous = mutableMapOf<PluginScopeId, MutableList<String>>()
        for (name in expectations.shadowedClasses + expectations.ambiguousClasses) {
            val defining = aggregate.definingScopes(name).map { PluginScopeId(it.id) }
            if (defining.size > 1) {
                defining.forEach { ambiguous.getOrPut(it) { mutableListOf() }.add(name) }
            }
            if (defining.isNotEmpty() && applicationLoader.getResource(name.replace('.', '/') + ".class") != null) {
                defining.forEach { shadowed.getOrPut(it) { mutableListOf() }.add(name) }
            }
        }

        val rows = PluginUniverseView.scopeDetails(scopes, contributions, classesByScope,
            shadowed = { shadowed[it]?.sorted() ?: listOf() },
            ambiguous = { ambiguous[it]?.sorted() ?: listOf() })

        for (scope in scopes.loadedFolders) {
            (scope.classLoader as? URLClassLoader)?.close()
        }
        return report(root, Mode.INSPECT, listOf(), rows, mapOf(), expectations)
    }


    fun verify(pluginRoot: Path, expectations: KitExpectations = KitExpectations()): KitReport {
        val root = pluginRoot.toAbsolutePath().normalize()
        val runtime = try {
            KzenAutoRuntime.initialize(KzenAutoRuntimeConfig(root))
        }
        catch (e: PluginBootException) {
            return report(root, Mode.VERIFY, e.errors, listOf(), mapOf(), expectations)
        }

        val moduleRoot = Files.createTempDirectory("kzen-kit-module")
        Files.createDirectories(moduleRoot.resolve("src/main/resources/notation/main"))
        val context = KzenAutoContext.create(KzenAutoConfig(
            jsModuleName = kitModuleName, moduleRoot = moduleRoot, workRoot = moduleRoot.resolve("work")))
        try {
            val availability = context.pluginAvailability
            for (name in expectations.availableClasses + expectations.unavailableClasses) {
                availability.of(ClassName(name))
            }
            for (name in expectations.shadowedClasses + expectations.ambiguousClasses + expectations.expressionClasses) {
                try {
                    Class.forName(name, false, runtime.aggregateClassLoader)
                }
                catch (_: Throwable) {
                    // recorded by the aggregate's diagnostics; the row shows it
                }
            }
            val identity = expectations.expressionClasses.sorted().associateWith { expressionIdentity(context, runtime, it) }
            val rows = PluginUniverseView.scopes(runtime, availability)
            return report(root, Mode.VERIFY, listOf(), rows, identity, expectations)
        }
        finally {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun inspectClass(
        className: ClassName,
        mirror: ReflectiveClassMirror,
        generated: List<ReflectionRegistry>,
        aggregate: AggregateClassLoader
    ): PluginClassDetail {
        val registry = generated.firstOrNull { it.contains(className) }
        val services: Collection<ClassName> = try {
            when {
                registry != null -> registry.serviceArguments(className).values
                mirror.contains(className) -> mirror.serviceArguments(className).values
                else -> return PluginClassDetail(className.asString(), PluginClassDetail.unresolvable,
                    if (aggregate.definingScopes(className.get()).isEmpty() && aggregate.getResource(className.get().replace('.', '/') + ".class") == null)
                        "not found in any scope"
                    else "not served by any registry or mirror (missing @Reflect?)")
            }
        }
        catch (e: IllegalArgumentException) {
            return PluginClassDetail(className.asString(), PluginClassDetail.unresolvable, e.message ?: e.toString())
        }
        val detail = if (services.isEmpty()) null else "needs @Service " + services.joinToString { it.asString() }
        return PluginClassDetail(className.asString(), PluginClassDetail.resolved, detail)
    }


    private fun definingScopes(
        className: ClassName,
        aggregate: AggregateClassLoader,
        scopes: PluginScopes,
        contributions: List<ScopeContributions>
    ): List<PluginScopeId> {
        val contributed = contributions.filter { it.generatedRegistry?.contains(className) == true }.map { it.scopeId }
        if (contributed.isNotEmpty()) {
            return contributed
        }
        val folders = aggregate.definingScopes(className.get()).map { PluginScopeId(it.id) }
        if (folders.isNotEmpty()) {
            return folders
        }
        val resource = className.get().replace('.', '/') + ".class"
        return if (scopes.application.requireClassLoader().getResource(resource) != null) listOf(scopes.application.id) else listOf()
    }


    private fun expressionIdentity(context: KzenAutoContext, runtime: KzenAutoRuntime, className: String): String {
        val probeName = "KitProbe_" + Integer.toHexString(className.hashCode())
        val code = KotlinCode(probeName, "class $probeName { fun type(): Class<*> = $className::class.java }")
        val loader = ClassLoaderUtils.dynamicParentClassLoader()
        context.cachedKotlinCompiler.tryCompile(code, loader)?.let { return "compile failed: $it" }
        val probe = context.cachedKotlinCompiler.tryLoad(code, loader)
            ?: return "compile failed"
        val resolved = probe.getMethod("type").invoke(probe.getDeclaredConstructor().newInstance()) as Class<*>
        val served = try {
            Class.forName(className, false, runtime.aggregateClassLoader)
        }
        catch (e: Throwable) {
            return "aggregate cannot serve: ${e.message}"
        }
        return if (resolved === served) KitReport.identical
        else "different Class: expression saw ${resolved.classLoader}, aggregate serves ${served.classLoader}"
    }


    private fun report(
        root: Path,
        mode: Mode,
        bootErrors: List<String>,
        rows: List<PluginScopeDetail>,
        identity: Map<String, String>,
        expectations: KitExpectations
    ): KitReport {
        val problems = mutableListOf<String>()
        if (expectations.bootErrors.isEmpty()) {
            bootErrors.forEach { problems.add("unexpected boot error: $it") }
        }
        else {
            for (expected in expectations.bootErrors) {
                if (bootErrors.none { expected in it }) {
                    problems.add("expected boot error containing '$expected'; got ${bootErrors.ifEmpty { listOf("none") }}")
                }
            }
        }

        val byId = rows.associateBy { it.id }
        for (id in expectations.loadedScopes) {
            val scope = byId[id]
            if (scope == null) problems.add("scope '$id' not found")
            else if (!scope.loaded) problems.add("scope '$id' failed to load: ${scope.failure}")
        }
        for (id in expectations.failedScopes) {
            val scope = byId[id]
            if (scope == null) problems.add("scope '$id' not found")
            else if (scope.loaded) problems.add("scope '$id' loaded but was expected to fail")
        }

        val readers = rows.flatMap { it.readers }.toSet()
        for (reader in expectations.readers) {
            if (reader !in readers) problems.add("reader '$reader' not discovered; found $readers")
        }
        val documents = rows.flatMap { it.documents }.map { it.path }.toSet()
        for (document in expectations.documents) {
            if (document !in documents) problems.add("document '$document' not discovered; found $documents")
        }

        val classes = rows.flatMap { it.classes }.associateBy { it.className }
        for (name in expectations.availableClasses) {
            val detail = classes[name]
            val ok = detail != null && when (mode) {
                Mode.INSPECT -> detail.availability == PluginClassDetail.resolved
                Mode.VERIFY -> detail.availability == PluginClassDetail.available
            }
            if (!ok) problems.add("class '$name' expected available; ${describe(detail)}")
        }
        for (name in expectations.unavailableClasses) {
            val detail = classes[name]
            val ok = detail != null && when (mode) {
                Mode.INSPECT -> detail.availability == PluginClassDetail.resolved && detail.detail != null
                Mode.VERIFY -> detail.availability == PluginClassDetail.unavailable
            }
            if (!ok) problems.add("class '$name' expected unavailable (a host service needed); ${describe(detail)}")
        }

        val ambiguous = rows.flatMap { it.ambiguousClasses }.toSet()
        for (name in expectations.ambiguousClasses) {
            if (name !in ambiguous) problems.add("class '$name' expected ambiguous across scopes; not reported")
        }
        val shadowed = rows.flatMap { it.shadowedClasses }.toSet()
        for (name in expectations.shadowedClasses) {
            if (name !in shadowed) problems.add("class '$name' expected shadowed by the application; not reported")
        }

        if (mode == Mode.INSPECT && expectations.expressionClasses.isNotEmpty()) {
            problems.add("expression identity needs verify mode (its own JVM)")
        }
        for ((name, result) in identity) {
            if (result != KitReport.identical) problems.add("expression identity for '$name': $result")
        }

        return KitReport(root, mode, bootErrors, rows, identity, problems)
    }


    private fun describe(detail: PluginClassDetail?): String {
        return if (detail == null) "not resolved" else detail.availability + (detail.detail?.let { " ($it)" } ?: "")
    }

}
