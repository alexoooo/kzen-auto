package tech.kzen.auto.common.objects.document.logic.context

import tech.kzen.lib.common.exec.engine.context.ContextFamily
import tech.kzen.lib.common.exec.engine.context.ContextKey
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


/**
 * Where a [ContextDescriptor] lives in a frame's registry — the third of the three layers a Context has
 * (nominal declaration → value contract → runtime address), derived here rather than authored.
 *
 * **The default family is the canonical rendering of the WHOLE type**, not merely its class name: nested
 * generic arguments and nullability participate, so `List<String>` and `List<Int>` do not silently share one
 * address. An explicit `key:` replaces that default and is the deliberate interop alias — the stable public
 * string a raw plugin call names, which is why the framework Contexts keep short ones (`browser`, `sut`).
 *
 * [canonicalFamily] is a **wire contract**, not a display concern: the same declaration must produce the same
 * address on JVM and JS, so it renders fully-qualified names and never reuses `TypeMetadata.toSimple()`,
 * whose import-shortening exists for a different purpose entirely.
 */
object ContextAddressing {
    //-----------------------------------------------------------------------------------------------------------------
    private const val genericsOpen = '<'
    private const val genericsClose = '>'
    private const val genericsSeparator = ","
    private const val nullableSuffix = '?'


    /** The canonical address family of a value contract: `kotlin.collections.List<kotlin.String>?`. */
    fun canonicalFamily(type: TypeMetadata): String {
        val generics =
            if (type.generics.isEmpty()) {
                ""
            }
            else {
                type.generics.joinToString(
                    genericsSeparator, "$genericsOpen", "$genericsClose") { canonicalFamily(it) }
            }

        val nullable = if (type.nullable) "$nullableSuffix" else ""

        return type.className.asString() + generics + nullable
    }


    /** The family [descriptor] addresses: its explicit interop alias, else the canonical rendering of its type. */
    fun familyOf(descriptor: ContextDescriptor): ContextFamily {
        return ContextFamily(descriptor.key.ifEmpty { canonicalFamily(descriptor.type) })
    }


    /**
     * The exact registry address of [descriptor], with [computedQualifier] applied when the declaration admits
     * one.
     *
     * **A declared qualifier always resolves exactly, and combining the two is an error** rather than an
     * override or a concatenation. The two kinds of qualifier carry different knowledge: a declared one is a
     * static fact the analysis and the runtime gate can both reason about exactly, while a computed one is a
     * step parameter no declaration can enumerate — so a declaration that already names its member has
     * nothing left for a run-time value to say, and silently letting one win would make the static claim and
     * the runtime address disagree.
     */
    fun keyOf(descriptor: ContextDescriptor, computedQualifier: String? = null): ContextKey {
        val family = familyOf(descriptor)

        if (descriptor.qualifier.isNotEmpty()) {
            require(computedQualifier.isNullOrEmpty()) {
                "${descriptor.label()} declares the qualifier '${descriptor.qualifier}', so it addresses one " +
                        "member exactly — passing '$computedQualifier' as well is ambiguous. Use an " +
                        "unqualified context declaration for a computed qualifier."
            }
            return ContextKey(family, descriptor.qualifier)
        }

        return ContextKey(family, computedQualifier?.ifEmpty { null })
    }
}
