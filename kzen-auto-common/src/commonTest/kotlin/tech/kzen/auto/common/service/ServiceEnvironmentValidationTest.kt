package tech.kzen.auto.common.service

import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.platform.ClassName
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


/**
 * Fresh registries throughout (never [ReflectionRegistry.global]) so the process-global stays clean.
 */
class ServiceEnvironmentValidationTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val consumerClass = "com.example.ServiceConsumer"
    private val serviceClass = "com.example.MissingService"


    private fun registryDeclaring(serviceClassName: String): ReflectionRegistry {
        val registry = ReflectionRegistry()
        registry.put(consumerClass, listOf("svc"), mapOf("svc" to serviceClassName)) { "instance" }
        return registry
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun missingServiceNamesBothSidesOfTheCoupling() {
        val error = assertFailsWith<IllegalStateException> {
            ServiceEnvironmentValidation.validate(GraphEnvironment.empty, registryDeclaring(serviceClass))
        }

        val message = error.message!!
        assertTrue(serviceClass in message, "missing service type not named: $message")
        assertTrue(consumerClass in message, "declaring class not named: $message")
    }


    @Test
    fun providedServicePasses() {
        val environment = GraphEnvironment
            .builder()
            .put(ClassName(serviceClass), "instance")
            .build()

        ServiceEnvironmentValidation.validate(environment, registryDeclaring(serviceClass))
    }


    @Test
    fun graphEnvironmentSelfReferencePasses() {
        // GraphEnvironment resolves to the environment itself, so it needs no explicit registration
        ServiceEnvironmentValidation.validate(
            GraphEnvironment.empty,
            registryDeclaring(GraphEnvironment.className.asString()))
    }


    @Test
    fun emptyRegistryPasses() {
        ServiceEnvironmentValidation.validate(GraphEnvironment.empty, ReflectionRegistry())
    }
}
