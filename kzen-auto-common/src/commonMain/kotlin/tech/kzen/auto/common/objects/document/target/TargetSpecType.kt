package tech.kzen.auto.common.objects.document.target

import tech.kzen.lib.common.model.definition.AttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * One target type's runtime instantiation — how the [TargetSpec] is built from the defined
 * value. Registered as an `is: TargetSpecType` notation object and autowired into
 * [TargetSpecCreator], matched by [typeName] — so adding a target type (including from a
 * third-party module) requires no edit to any shared file.
 *
 * The DEFINE side is declarative: the notation object states the `type:` name it handles
 * (`typeName:`) and its value shape (`valueKind: none | text | reference`), which
 * [TargetSpecDefiner] reads straight from notation. (A definer object cannot take autowired
 * instances — definers are instantiated mid-definition, before the handler objects exist —
 * so the define side carries no code.) The [typeName] here must equal the notation object's
 * `typeName:`.
 *
 * The server-side locate counterpart is TargetTypeLocator (kzen-auto-jvm); the client
 * editor/summary counterpart is TargetTypeDisplay (kzen-auto-js).
 */
abstract class TargetSpecType {
    /** Resolves the `type:` key of the `target:` notation map (must equal the registration
     *  object's `typeName:`). */
    abstract val typeName: String


    /** Instantiate the runtime spec from the value definition (per the registered `valueKind:`
     *  — null for `none`, a value definition for `text`, a reference definition for
     *  `reference`). */
    abstract fun createSpec(
        valueDefinition: AttributeDefinition?,
        policy: TargetMatchPolicy,
        objectLocation: ObjectLocation,
        partialGraphInstance: GraphInstance
    ): TargetSpec
}
