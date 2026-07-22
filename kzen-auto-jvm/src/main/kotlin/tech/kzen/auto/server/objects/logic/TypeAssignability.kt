package tech.kzen.auto.server.objects.logic

import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.KotlinCode
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


/**
 * Static [TypeMetadata]-to-[TypeMetadata] assignability, by Kotlin's own rules: a PROBE COMPILE of
 * `fun probe(value: <source>): <target> { return value }` — the probe compiles iff a [source] value can be
 * assigned to a [target] slot, so subtyping (`Int` → `Number`), generics variance and nullability all come from
 * the compiler rather than a hand-rolled type-hierarchy walk. The mechanism is
 * [tech.kzen.auto.server.objects.script.step.eval.ResultStep]'s declared-type conformance check (forced
 * return type ⇒ compile error on mismatch) transposed to a pair of already-inferred
 * types; the probe is never loaded or executed, and [CachedKotlinCompiler] content-caches it, so a repeated
 * check of the same pair costs a cache hit.
 */
object TypeAssignability {
    // Fixed probe class name: the compile cache keys on className + source digest, and the source encodes
    // both types, so distinct pairs never collide; the class is never loaded, so no name uniqueness is needed.
    private const val probeClassName = "AssignabilityProbe"


    /** True when a value of [source] can be assigned to [target]. */
    fun isAssignable(
        source: TypeMetadata,
        target: TypeMetadata,
        cachedKotlinCompiler: CachedKotlinCompiler,
        classLoader: ClassLoader
    ): Boolean {
        // The StepExpressionCompiler import pattern: import every class either type mentions, render both
        // as simple names.
        val imports = (source.classNames() + target.classNames())
            .joinToString("\n") { "import $it" }

        val code = KotlinCode(
            probeClassName,
            """
$imports

class $probeClassName {
    fun probe(value: ${source.toSimple()}): ${target.toSimple()} { return value }
}
""")

        return cachedKotlinCompiler.tryCompile(code, classLoader) == null
    }
}
