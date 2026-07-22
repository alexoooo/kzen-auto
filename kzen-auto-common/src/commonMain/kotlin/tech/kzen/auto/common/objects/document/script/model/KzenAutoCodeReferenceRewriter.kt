package tech.kzen.auto.common.objects.document.script.model

import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.auto.common.paradigm.logic.LogicConventions
import tech.kzen.auto.common.util.ExpressionUtils
import tech.kzen.auto.common.util.KotlinExpressionAnalyzer
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.service.notation.CodeReferenceRewriter


/**
 * Rewrites references to a renamed object (a Script step, parameter, or loop-item; a Job parameter) that are
 * embedded as variable identifiers inside code attributes (Formula / DoWhile / Filter `where` / FormulaSource
 * `code`) — the kzen-auto implementation of the kzen-lib [CodeReferenceRewriter] hook, applied as part of the
 * rename refactor (see `NotationReducer.renameObjectRefactor`).
 *
 * In a Script, scope is resolved exactly as `StepExpressionCompiler` resolves it (ScriptTree predecessors +
 * in-scope bindings), so a bare identifier is only rewritten in an expression where the renamed object is
 * genuinely in scope. This disambiguates same-named objects in different branches: within any single
 * expression's scope names are unique (otherwise the generated accessors would not compile), so at most one
 * candidate ever matches. In a Job, the `parameters` declarations are the only bare-referenceable objects and
 * each is in scope of every Worker expression, so a renamed parameter rewrites document-wide.
 *
 * Platform-agnostic (commonMain) and deterministic, so the client's optimistic apply and the server's
 * authoritative apply of a rename produce identical results.
 */
object KzenAutoCodeReferenceRewriter: CodeReferenceRewriter {
    override fun renameObjectReferences(
        oldLocation: ObjectLocation,
        newLocation: ObjectLocation,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): List<UpdateInAttributeCommand> {
        val oldName = oldLocation.objectPath.name.value
        val newName = newLocation.objectPath.name.value
        if (oldName == newName) {
            return listOf()
        }

        // The bare identifier the old name maps to (back-ticks stripped), and the escaped form of the new name.
        val oldIdentifierContent = ExpressionUtils.identifierContent(
            ExpressionUtils.escapeKotlinVariableName(oldName))
        val newEscapedName = ExpressionUtils.escapeKotlinVariableName(newName)

        val documentPath = oldLocation.documentPath
        val coalesce = graphDefinitionAttempt.graphStructure.graphNotation.coalesce

        // Candidate value-scalar attributes (e.g. a Formula's `code`) in this document that lexically reference
        // the renamed identifier. Reference-typed attributes are excluded (kzen-lib adjusts those generically),
        // as is the renamed object's own notation.
        val candidates = mutableListOf<Triple<ObjectLocation, AttributePath, String>>()
        for (location in coalesce.map.keys) {
            if (location.documentPath != documentPath || location == oldLocation) {
                continue
            }
            val objectDefinition = graphDefinitionAttempt.objectDefinitions[location]
                ?: continue
            val objectNotation = coalesce[location]
                ?: continue

            for ((attributeName, attributeDefinition) in objectDefinition.attributeDefinitions.map) {
                if (attributeDefinition !is ValueAttributeDefinition) {
                    continue
                }
                val attributeNotation = objectNotation.attributes.map[attributeName] as? ScalarAttributeNotation
                    ?: continue
                if (oldIdentifierContent in KotlinExpressionAnalyzer.referencedIdentifiers(attributeNotation.value)) {
                    candidates.add(Triple(location, AttributePath.ofName(attributeName), attributeNotation.value))
                }
            }
        }

        if (candidates.isEmpty()) {
            return listOf()
        }

        // Scope dispatch by document flavour. In a JOB, only the `parameters` declarations are referenceable by
        // bare identifier, and each is in scope of EVERY Worker's expression in the document — so a renamed
        // parameter rewrites all lexical candidates, and any other Job rename (a Worker, a Channel) rewrites
        // nothing. In a SCRIPT (and any other flavour), scope is resolved exactly as StepExpressionCompiler
        // resolves it (ScriptTree predecessors + in-scope bindings) — only building the tree once a candidate
        // exists; `successful()` simply wraps the definitions, and ScriptTree.read reads only the notation
        // structure, so a failed sibling definition is fine.
        val documentNotation = graphDefinitionAttempt.graphStructure.graphNotation.documents[documentPath]
        val renamedInScopeOf: (ObjectPath) -> Boolean =
            if (documentNotation != null && JobConventions.isJob(documentNotation)) {
                val renamedIsParameter = oldLocation.objectPath.nesting.segments.lastOrNull()
                    ?.attributePath == LogicConventions.parametersAttributePath
                ({ renamedIsParameter })
            }
            else {
                val scriptTree = ScriptTree.read(documentPath, graphDefinitionAttempt.successful())
                val renamedObjectPath = oldLocation.objectPath
                val inScopeCache = mutableMapOf<ObjectPath, Boolean>()
                ({ objectPath ->
                    inScopeCache.getOrPut(objectPath) {
                        renamedObjectPath in scriptTree.inScopeReferencePaths(objectPath)
                    }
                })
            }

        val commands = mutableListOf<UpdateInAttributeCommand>()
        for ((objectLocation, attributePath, code) in candidates) {
            if (!renamedInScopeOf(objectLocation.objectPath)) {
                continue
            }
            val rewritten = KotlinExpressionAnalyzer.renameIdentifier(code, oldIdentifierContent, newEscapedName)
            if (rewritten == code) {
                continue
            }
            commands.add(UpdateInAttributeCommand(
                objectLocation, attributePath, ScalarAttributeNotation(rewritten)))
        }
        return commands
    }
}
