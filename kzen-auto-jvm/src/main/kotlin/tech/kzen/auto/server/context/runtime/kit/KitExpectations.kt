package tech.kzen.auto.server.context.runtime.kit


/**
 * What a plugin author states about a plugin directory, checked by [PluginCompatibilityKit]. Every set is
 * optional; an empty set asserts nothing. Class names are binary names; reader identities are
 * `namespace.name@compatibility`; document paths are logical notation paths (`auto-jvm/x/y.yaml`).
 */
data class KitExpectations(
    /** Scopes (directory or manifest ids) that must load. */
    val loadedScopes: Set<String> = setOf(),

    /** Scopes that must be present but fail to load (a deliberately broken fixture). */
    val failedScopes: Set<String> = setOf(),

    /** Substrings, each of which must appear in some boot error; non-empty means boot is expected to fail. */
    val bootErrors: List<String> = listOf(),

    /** Reader identities that must be discovered. */
    val readers: Set<String> = setOf(),

    /** Bundled document paths that must be discovered. */
    val documents: Set<String> = setOf(),

    /** `@Reflect` classes a standalone workspace (kzen's own services only) must be able to instantiate. */
    val availableClasses: Set<String> = setOf(),

    /** `@Reflect` classes that must resolve but need a host service a standalone workspace lacks. */
    val unavailableClasses: Set<String> = setOf(),

    /** Class names that must be reported as defined by more than one scope. */
    val ambiguousClasses: Set<String> = setOf(),

    /** Class names that must be reported as shadowed by the application classpath. */
    val shadowedClasses: Set<String> = setOf(),

    /** Classes an expression must resolve to the very `Class` the aggregate loader serves (verify mode). */
    val expressionClasses: Set<String> = setOf()
)
