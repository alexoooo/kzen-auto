package tech.kzen.auto.server.util

import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.allSupertypes


/**
 * Reflectively recovers the element type E of a type that is `Iterable<E>` but exposes no generic
 * parameter of its own — e.g. `IntRange : Iterable<Int>`, `CharRange : Iterable<Char>`, or any concrete
 * class that fixes the type argument. This replaces per-type special-casing: the `Iterable<E>` fact lives
 * in the class hierarchy, not in the static [TypeMetadata] (whose `generics` is empty for such types), so
 * the only general way to read it is reflection over the supertypes.
 *
 * Parameterized collections (`List<X>`, `Set<X>`, …) already carry the element type as their generic
 * argument, so callers should read that first and only fall back here.
 */
object IterableElementTypeReflect {
    private val iterableQualifiedName = Iterable::class.qualifiedName


    // The element type of `Iterable<E>` for the given class, or null when the class can't be loaded, isn't
    // an Iterable, or leaves E unresolved (an unbound type variable — unknowable without the missing
    // generic argument).
    fun elementType(className: ClassName): TypeMetadata? {
        val kClass = loadKotlinClass(className)
            ?: return null

        val iterableType = kClass.allSupertypes.firstOrNull {
            (it.classifier as? KClass<*>)?.qualifiedName == iterableQualifiedName
        } ?: return null

        val elementType = iterableType.arguments.firstOrNull()?.type
            ?: return null

        return elementType.toTypeMetadata()
    }


    private fun loadKotlinClass(className: ClassName): KClass<*>? {
        return try {
            Class.forName(className.asString(), false, ClassLoaderUtils.dynamicParentClassLoader()).kotlin
        }
        catch (e: Throwable) {
            null
        }
    }


    private fun KType.toTypeMetadata(): TypeMetadata? {
        val kClass = classifier as? KClass<*>
            ?: return null
        val qualifiedName = kClass.qualifiedName
            ?: return null

        val generics = arguments.map { it.type?.toTypeMetadata() ?: TypeMetadata.any }

        return TypeMetadata(ClassName(qualifiedName), generics, isMarkedNullable)
    }
}
