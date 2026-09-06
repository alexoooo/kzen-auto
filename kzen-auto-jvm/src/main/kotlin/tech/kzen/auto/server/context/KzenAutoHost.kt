package tech.kzen.auto.server.context

import tech.kzen.lib.platform.ClassName


/**
 * Services an embedding host hands to a context, keyed by the type each `@Service` constructor parameter
 * declares. Merged into the context's [tech.kzen.lib.common.service.context.environment.GraphEnvironment] after
 * kzen's own entries; a key kzen already provides fails context creation by name. The Java builder is keyed by
 * `Class<?>`, so a host never spells a kzen name and can register a proxy under the interface a Worker declares
 * rather than under its runtime class; the key must be assignable from the instance.
 */
class KzenAutoHost private constructor(
    val services: Map<ClassName, Any>
) {
    companion object {
        val empty = KzenAutoHost(mapOf())

        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }


    class Builder {
        private val services = linkedMapOf<ClassName, Any>()

        /** Registers [instance] under [type]; a second registration of the same type fails by name. */
        fun <T: Any> service(type: Class<T>, instance: T): Builder {
            require(type.isInstance(instance)) {
                "${instance.javaClass.name} is not a ${type.name}"
            }
            val className = ClassName(type.name)
            check(className !in services) {
                "Host service already registered: ${type.name}"
            }
            services[className] = instance
            return this
        }

        inline fun <reified T: Any> service(instance: T): Builder {
            return service(T::class.java, instance)
        }

        fun build(): KzenAutoHost {
            return KzenAutoHost(services.toMap())
        }
    }
}
