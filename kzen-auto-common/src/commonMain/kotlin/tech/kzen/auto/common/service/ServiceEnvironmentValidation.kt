package tech.kzen.auto.common.service

import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.service.context.environment.GraphEnvironment


/**
 * Guards the coupling between the @Service parameter types the reflection registry records and the service
 * types the host environment provides. Without this, a mismatch (a typo in the hand-written Kotlin/JS
 * ClassName literals, or a package rename on either side) surfaces only at graph-creation time, as a
 * "Missing service" deep inside the create chain.
 */
object ServiceEnvironmentValidation {
    /**
     * Asserts every @Service parameter type recorded in [reflectionRegistry] is resolvable from [environment].
     * Call at boot, after all module register() calls and environment construction; re-invokable after late
     * module registration (e.g. plugin load).
     *
     * Validation is one-directional: environment services with no @Service consumer are legitimate (they are
     * consumed by hand resolve() calls, or by modules registered downstream).
     */
    fun validate(
        environment: GraphEnvironment,
        reflectionRegistry: ReflectionRegistry = ReflectionRegistry.global
    ) {
        val missing = reflectionRegistry
            .serviceArgumentDeclarations()
            .filterKeys { !environment.contains(it) }

        // All misses in one throw - a rename that breaks several types should read as one actionable list
        check(missing.isEmpty()) {
            missing.entries.joinToString("\n") { (serviceClassName, declaringClassNames) ->
                "@Service type not registered in GraphEnvironment: ${serviceClassName.asString()}" +
                        " (declared by: ${declaringClassNames.joinToString { it.asString() }})"
            } + "\nenvironment provides: ${environment.serviceClassNames.map { it.asString() }.sorted()}"
        }
    }
}
