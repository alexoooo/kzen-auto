package tech.kzen.auto.client.objects.document.script.display

import tech.kzen.auto.client.objects.document.common.attribute.AttributeWrapperLookup
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.common.objects.document.logic.context.ContextDescriptor
import tech.kzen.auto.common.objects.document.logic.context.LogicContextConventions
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.ObjectMetadata
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation


//---------------------------------------------------------------------------------------------------------------------
/**
 * What a step's card shows beyond its header line and its trace: the attributes the expanded body renders, the
 * two conditions under which the body must not repeat a message a field below it already carries, and the
 * run-scoped Context declarations the header badges.
 *
 * Held as one immutable value per publish rather than as a dozen state fields, so the display's skip guard is a
 * single `==`. Value equality is the point: every field here is rebuilt on each derivation, so a reference guard
 * would never bail and every sibling's edit would re-render every step body (`docs/js-architecture.md` §2).
 */
data class StepContentInfo(
    val objectMetadata: ObjectMetadata,
    val summaryAttributeNames: List<AttributeName>,

    // This step's object has attribute-level definition failures — surfaced per-field by the attribute editors —
    // so the (redundant, less specific) step-level validation message is suppressed in the body.
    val hasFieldDefinitionError: Boolean,

    // One of this step's attributes is edited as a Kotlin expression, which prints the step's validation message
    // under itself and marks the offending token — so the body suppresses its own copy.
    val hasExpressionField: Boolean,

    val bindsContext: ContextDescriptor?,
    val closePolicy: String?,
    val bindsExported: Boolean?,
    val hostedExports: List<ContextDescriptor>?,
    val hostedExportsContinuingUp: List<ContextDescriptor>?,
    val usesContexts: List<ContextDescriptor>,
    val releasesContext: ContextDescriptor?
)


//---------------------------------------------------------------------------------------------------------------------
// Read straight off notation for the binds badge's tooltip. The nullable AttributePath overload, because a step
// that owns no resource has no such attribute (the AttributeName overload throws).
private val closePolicyAttributePath = AttributePath.ofName(AttributeName("closePolicy"))

// The Script flavour's Kotlin-expression field editor, named by an attribute's `editor:` metadata. Self-reference
// within the Script display: this card and that editor are the two halves of one decision — the field renders a
// step's validation message inline, so the card must not repeat it.
private val expressionEditorName = ObjectName("KotlinExpressionEditor")


/**
 * NB: the caller reaches this only once the step's metadata is present ([computeStepHeaderInfo] returned
 * non-null), which is what makes the objectMetadata lookup safe to force.
 */
fun computeStepContentInfo(
    clientState: ClientState,
    stepLocation: ObjectLocation
): StepContentInfo {
    val graphStructure = clientState.graphStructure()
    val objectMetadata = graphStructure
        .graphMetadata
        .objectMetadata[stepLocation]!!

    val hasFieldDefinitionError = clientState
        .graphDefinitionAttempt
        .failures.map[stepLocation]
        ?.attributeErrors?.isNotEmpty() == true

    // Attribute-definition inspection, not a step-type list: any object whose notation names the expression
    // editor on an attribute gets the field-level message, including one a plugin declares.
    val hasExpressionField = objectMetadata.attributes.map.values.any {
        AttributeWrapperLookup.wrapperName(it, AttributeWrapperLookup.editorAttributePath) == expressionEditorName
    }

    val graphNotation = graphStructure.graphNotation

    val bindsContext = LogicContextConventions.stepBinds(graphNotation, stepLocation)

    // Read unconditionally — binding a Context and owning a resource are independent declarations
    // (ContextBinder carries `binds`, ResourceOwner carries `closePolicy`), so a step may own a resource
    // without binding a Context, and vice versa. Absence is tolerated at both levels: the nullable
    // AttributePath overload returns null when the attribute isn't declared, and the cast covers a
    // non-scalar notation.
    val closePolicy =
        (graphNotation.firstAttribute(stepLocation, closePolicyAttributePath) as? ScalarAttributeNotation)
            ?.value

    val documentExports = LogicContextConventions.documentExports(graphNotation, stepLocation.documentPath)

    // Whether the bound value leaves this document: exported means the caller takes ownership, un-exported means
    // it is private and dies at this document's settle. Both are legitimate, and the badge's tooltip says which —
    // a verified claim about THIS document's own signature, not a guess about a caller the editor cannot see.
    val bindsExported = bindsContext?.let { bound ->
        documentExports.any { it.location == bound.location }
    }

    // A RunStep's counterpart: what the hosted document hands up, and which of those this document passes further
    // up rather than owning. One notation read each, no graph walk.
    val hostedPath = when {
        ScriptConventions.isRunStep(graphNotation, stepLocation) ->
            ScriptConventions.hostedDocumentPath(graphNotation, stepLocation)

        else -> null
    }

    val hostedExports = hostedPath?.let { LogicContextConventions.documentExports(graphNotation, it) }

    val hostedExportsContinuingUp = hostedExports?.filter { hosted ->
        documentExports.any { it.location == hosted.location }
    }

    return StepContentInfo(
        objectMetadata = objectMetadata,
        summaryAttributeNames = findSummaryAttributes(objectMetadata),
        hasFieldDefinitionError = hasFieldDefinitionError,
        hasExpressionField = hasExpressionField,
        bindsContext = bindsContext,
        closePolicy = closePolicy,
        bindsExported = bindsExported,
        hostedExports = hostedExports,
        hostedExportsContinuingUp = hostedExportsContinuingUp,
        usesContexts = LogicContextConventions.stepUses(graphNotation, stepLocation),
        releasesContext = LogicContextConventions.stepReleases(graphNotation, stepLocation))
}


private fun findSummaryAttributes(objectMetadata: ObjectMetadata): List<AttributeName> {
    return objectMetadata
        .attributes
        .map
        .filterValues {
            AttributeWrapperLookup.wrapperName(it, AttributeWrapperLookup.summaryAttributePath) != null
        }
        .keys
        .toList()
}
